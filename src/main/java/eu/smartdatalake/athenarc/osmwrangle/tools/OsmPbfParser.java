/*
 * @(#) OsmPbfParser.java	version 2.0   5/12/2019
 *
 * Copyright (C) 2013-2019 Information Management Systems Institute, Athena R.C., Greece.
 *
 * This library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.smartdatalake.athenarc.osmwrangle.tools;

import crosby.binary.osmosis.OsmosisReader;
import eu.smartdatalake.athenarc.osmwrangle.osm.*;
import eu.smartdatalake.athenarc.osmwrangle.utils.*;
import org.apache.commons.io.FilenameUtils;
import org.geotools.api.referencing.crs.CRSAuthorityFactory;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Entry point to parse OpenStreetMap (OSM) PBF (compressed) files into CSV tuples and/or RDF triples using Osmosis.
 * LIMITATIONS: - Depending on system and JVM resources, transformation can handle only a moderate amount of OSM features.
 * - RML transformation mode not currently supported.
 *
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 2/7/2018
 * Modified: 2/7/2018; added filters for tags in order to assign categories to extracted OSM features according to a user-specified classification scheme (defined in an extra YML file).
 * Modified: 4/7/2018; reorganized identification of categories based on OSM tags
 * Modified: 27/9/2018; excluded creation of linear ring geometries for roads and barriers; polygons are created instead
 * Modified; 24/10/2018; allowing transformation to proceed even in case that no filters (using OSM tags) have been specified; no classification scheme will be used in this case.
 * Modified; 5/12/2019; allowing extraction of unnamed entities; also enabling control whether to transform closed linear rings into polygons
 * Last modified by: Kostas Patroumpas, 5/12/2019
 */
public class OsmPbfParser implements Sink {

    private static final Logger LOGGER = LoggerFactory.getLogger(OsmPbfParser.class);

    Converter myConverter;
    Assistant myAssistant;
    ValueChecker myChecker;
    private MathTransform reproject = null;
    int sourceSRID;                       //Source CRS according to EPSG
    int targetSRID;                       //Target CRS according to EPSG
    private Configuration currentConfig;  //User-specified configuration settings
    private String inputFile;             //Input OSM XML file
    private String outputFile;            //Output RDF file

    private boolean keepUnnamed = true;        //Controls whether unnamed entities will be transformed
    private boolean closedRings2Polygons = false;    //Controls whether closed rings (i.e., first vertex coincides with the last) will be converted to polygons

    OsmosisReader reader;                 //Osmosis reader for parsing the OSM PBF file

    Classification classification = null; //Classification hierarchy for assigning categories to features

    //Initialize a CRS factory for possible reprojections
    private static final CRSAuthorityFactory crsFactory = ReferencingFactoryFinder
            .getCRSAuthorityFactory("EPSG", new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE));

    long numNodes;
    long numWays;
    long numRelations;
    long numNamedEntities;

    private GeometryFactory geometryFactory = new GeometryFactory();

    private OSMRecordBuilder recBuilder;

    private OSMNode nodeTmp;                             //the current OSM node object
    private OSMWay wayTmp;                               //the current OSM way object
    private OSMRelation relationTmp;                     //the current OSM relation object

    private Set<String> tags;                            //OSM tags used in the filters

    private boolean inWay = false;                       //when parser is in a way node becomes true in order to track the parser position
    private boolean inNode = false;                      //becomes true when the parser is in a simple node
    private boolean inRelation = false;                  //becomes true when the parser is in a relation node

    private boolean scanWays = true;                     //Activates preliminary scanning of OSM ways in order to create index structures required during parsing
    private boolean scanRelations = true;                //Activates preliminary scanning of OSM relations in order to create index structures required during parsing
    private boolean rescanRelations = false;             //Activates an auxiliary scan of OSM relations that may be referenced by other relations
    private boolean keepIndexed = false;                 //Determines whether to index references of a given OSM element based on its tags; discarded if none of its tags matches with the user-specified OSM filters

    /**
     * Constructor for the transformation process from OpenStreetMap PBF file to CSV/RDF.
     *
     * @param config     Parameters to configure the transformation.
     * @param inFile     Path to input OSM XML file.
     * @param outFile    Path to the output file that collects CSV tuples/RDF triples.
     * @param sourceSRID Spatial reference system (EPSG code) of the input OSM XML file.
     * @param targetSRID Spatial reference system (EPSG code) of geometries in the output CSV tuples and/or RDF triples.
     */
    public OsmPbfParser(Configuration config, String inFile, String outFile, int sourceSRID, int targetSRID) {

        currentConfig = config;
        inputFile = inFile;
        outputFile = outFile;
        this.sourceSRID = sourceSRID;                      //Assume that OSM input is georeferenced in WGS84
        this.targetSRID = targetSRID;
        myAssistant = new Assistant(config);
        myChecker = new ValueChecker();

        // Determine whether to keep or drop unnamed OSM features
        keepUnnamed = config.keepUnnamedEntities;

        //Get filter definitions over combinations of OSM tags in order to determine POI categories
        try {
            OSMClassification osmClassific = new OSMClassification(config.classificationSpec, currentConfig.outputDir);
            String classFile = osmClassific.apply();
            tags = osmClassific.getTags();

            //Instantiate a record builder to be used in handling each OSM record
            recBuilder = new OSMRecordBuilder(osmClassific.getFilters());

            //Create the internal representation of this classification scheme
            if (tags != null) {
                String outClassificationFile = currentConfig.outputDir + FilenameUtils.getBaseName(currentConfig.classificationSpec) + ".nt";
                classification = new Classification(currentConfig, classFile, outClassificationFile);
            }

        } catch (Exception e) {
            LOGGER.error("Cannot initialize parser for OSM data. Missing or malformed YML file with classification of OSM tags into categories.");
        }

        //Check if a coordinate transform is required for geometries
        if (currentConfig.targetCRS != null) {
            try {
                boolean lenient = true; // allow for some error due to different datums
                CoordinateReferenceSystem sourceCRS = crsFactory.createCoordinateReferenceSystem(currentConfig.sourceCRS);
                CoordinateReferenceSystem targetCRS = crsFactory.createCoordinateReferenceSystem(currentConfig.targetCRS);
                reproject = CRS.findMathTransform(sourceCRS, targetCRS, lenient);

                //Needed for parsing original geometry in WTK representation
                GeometryFactory geomFactory = new GeometryFactory(new PrecisionModel(), sourceSRID);
                myAssistant.wktReader = new WKTReader(geomFactory);

            } catch (Exception e) {
                LOGGER.error("Error in CRS transformation (reprojection) of geometries.");      //Execution terminated abnormally
            }
        } else {                            //No transformation specified; determine the CRS of geometries...
            this.targetSRID = 4326;          //... as the original OSM features assumed in WGS84 lon/lat coordinates
        }
        //Other parameters
        if (myAssistant.isNullOrEmpty(currentConfig.defaultLang)) {
            currentConfig.defaultLang = "en";
        }
    }

    /**
     * Calls Osmosis to parse the input PBF file.
     */
    public void parseDocument() {

        //Depending of input file size, determine if indices will be kept in-memory of will be disk-based
        File inFile = new File(inputFile);
        if (inFile.length() < 0.02 * Runtime.getRuntime().maxMemory()) {          //CAUTION! Rule of thumb: Input PBF file size is less than 5% of the JVM heap size, so memory is expected to be sufficient for indexing OSM elements
            //OPTION #1: Memory-based native Java structures for indexing
            recBuilder.nodeIndex = new OSMMemoryIndex();
            recBuilder.wayIndex = new OSMMemoryIndex();
            recBuilder.relationIndex = new OSMMemoryIndex();
            System.out.println("Building in-memory indices over OSM elements...");
        } else {                                               //For larger OSM files, resort to disk-based indexing of their referenced elements
            //OPTION #2: Disk-based structures for indexing
            recBuilder.nodeIndex = new OSMDiskIndex(currentConfig.tmpDir, "nodeIndex");
            recBuilder.wayIndex = new OSMDiskIndex(currentConfig.tmpDir, "wayIndex");
            recBuilder.relationIndex = new OSMDiskIndex(currentConfig.tmpDir, "relationIndex");
            System.out.println("Building disk-based indices over OSM elements...");
        }

        //This list will hold OSM relations that depend on other relations, so these must be checked once the entire OSM file is exhausted.
        recBuilder.incompleteRelations = new ArrayList<>();

        try {
            //Preliminary INDEXING phase: first scan of OSM relations to build the required index structures
            scanRelations = true;
            scanWays = false;
            System.out.println("Scanning OSM relations to identify indexed OSM elements...");
            parse();    //Call Osmosis parser to identify OSM elements
            System.out.println("Indexed " + recBuilder.nodeIndex.size() + " nodes, " + recBuilder.wayIndex.size() + " ways, and " + recBuilder.relationIndex.size() + " relations.");

            if (rescanRelations) {
                //Preliminary INDEXING phase: second scan of OSM relations to build the required index structures, since relations may refer to other relations
                scanRelations = true;
                scanWays = false;
                System.out.println("Second scan of OSM relations to identify other referenced OSM relations...");
                parse();    //Call Osmosis parser to identify OSM elements
                System.out.println("Indexed " + recBuilder.nodeIndex.size() + " nodes, " + recBuilder.wayIndex.size() + " ways, and " + recBuilder.relationIndex.size() + " relations.");
            }

            //Preliminary INDEXING phase: only scan OSM ways to build the required index structures
            scanWays = true;
            scanRelations = false;
            System.out.println("Scanning OSM ways to identify indexed OSM elements...");
            parse();    //Call Osmosis parser to identify OSM elements
            System.out.println("Indexed " + recBuilder.nodeIndex.size() + " nodes, " + recBuilder.wayIndex.size() + " ways, and " + recBuilder.relationIndex.size() + " relations.");

            //PARSING phase: Take advantage of precomputed indices when parsing
            scanRelations = false;
            scanWays = false;
            System.out.println("Starting parsing of all OSM elements...");
            parse();    //Call Osmosis parser to identify OSM elements

            //Second pass over incomplete OSM relations, once the entire XML file has been parsed
            for (Iterator<OSMRelation> iterator = recBuilder.incompleteRelations.iterator(); iterator.hasNext(); ) {
                OSMRelation r = iterator.next();
//	            	System.out.print("Re-examining OSM relation " + r.getID() + "...");
                OSMRecord rec = recBuilder.createOSMRecord(r);
                if (rec != null)                    //Incomplete relations are accepted in this second pass, consisting of their recognized parts
                {
                    if (keepUnnamed) {
                        myConverter.parse(rec, classification, reproject, targetSRID);
                    } else if (r.getTagKeyValue().containsKey("name")) {  //CAUTION: Only named entities will be transformed
                        myConverter.parse(rec, classification, reproject, targetSRID);
                        numNamedEntities++;
                    }
                    //Keep this relation geometry in the dictionary, just in case it might be referenced by other OSM relations
                    if (recBuilder.relationIndex.containsKey(r.getID())) {
                        recBuilder.relationIndex.put(r.getID(), rec.getGeometry());
                    }
                    numRelations++;
                    iterator.remove();                                  //This OSM relation should not be examined again
//	            		System.out.println("Done!");
                } else {
                    System.out.println(" Transformation failed!");
                }
            }

            if (inRelation) {
                System.out.println("\nFinished parsing OSM relations.");
            }
            recBuilder.nodeIndex.clear();                                            //Discard index over OSM nodes
            recBuilder.wayIndex.clear();                                            //Discard index over OSM ways
            recBuilder.relationIndex.clear();                                       //Discard index over OSM relations
        } catch (Exception e) {
            LOGGER.error("Cannot parse input file.");
        }
    }

    /**
     * Instantiates and calls an Osmosis parser to identify OSM elements.
     */
    private void parse() {
        //Initialize Osmosis parser for the input OSM PBF file
        try {
            InputStream inputStream = new FileInputStream(inputFile);
            reader = new OsmosisReader(inputStream);
            reader.setSink(this);
        } catch (Exception e) {
            LOGGER.error("Cannot intitialize Osmosis parser for the OSM PBF file.");
        }

        reader.run();     //Call Osmosis parser to identify OSM elements
    }


    /**
     * Initializes the Osmosis object.
     *
     * @param metaData Meta data applicable to this pipeline invocation.
     */
    @Override
    public void initialize(Map<String, Object> metaData) {
    }


    /**
     * Processes an OSM element (node, way, or relation).
     *
     * @param entityContainer Container of an OSM node, way, or relation.
     */
    @Override
    public void process(EntityContainer entityContainer) {

        keepIndexed = false;                  //Will become true only if a tag related to user-specified filters is found for the current OSM element

        if ((!scanWays) && (!scanRelations) && (entityContainer instanceof NodeContainer)) {                    //Create a new OSM node object and populate it with the appropriate values

            //Mark position of the parser
            inNode = true;
            inWay = false;
            inRelation = false;
            numNodes++;

            Node myNode = ((NodeContainer) entityContainer).getEntity();

            nodeTmp = new OSMNode();
            nodeTmp.setID("" + myNode.getId());

            //Collect tags associated with this OSM element
            for (Tag myTag : myNode.getTags()) {
                nodeTmp.setTagKeyValue(myTag.getKey(), myChecker.removeIllegalChars(myTag.getValue()));
                if ((tags == null) || (tags.contains(myTag.getKey()))) {            //CAUTION! Filter out any OSM elements not related to tags specified by the user
                    keepIndexed = true;                                             //In case of no tags specified for filtering, index all nodes
                }
            }

            //Create geometry object with original WGS84 coordinates
            Geometry geom = geometryFactory.createPoint(new Coordinate(myNode.getLongitude(), myNode.getLatitude()));
            nodeTmp.setGeometry(geom);

            //Convert entity
            if (keepIndexed) {
                if (keepUnnamed) {
                    myConverter.parse(recBuilder.createOSMRecord(nodeTmp), classification, reproject, targetSRID);
                } else if (nodeTmp.getTagKeyValue().containsKey("name")) {  //CAUTION! Only named entities will be transformed
                    myConverter.parse(recBuilder.createOSMRecord(nodeTmp), classification, reproject, targetSRID);
                    numNamedEntities++;
                }
            }

            if (recBuilder.nodeIndex.containsKey(nodeTmp.getID())) {
                recBuilder.nodeIndex.put(nodeTmp.getID(), nodeTmp.getGeometry());         //Keep a dictionary of node geometries, only if referenced by OSM ways
            }
            nodeTmp = null;

        } else if ((!scanRelations) && (entityContainer instanceof WayContainer)) {       //Create a new OSM way object and populate it with the appropriate values

            Way myWay = ((WayContainer) entityContainer).getEntity();

            for (Tag myTag : myWay.getTags()) {
                if ((tags == null) || (tags.contains(myTag.getKey()))) {            //CAUTION! Filter out any OSM elements not related to tags specified by the user
                    keepIndexed = true;                                             //In case of no tags specified for filtering, index all ways
                    break;
                }
            }

            if (scanWays) {
                //Either this OSM way is filtered or referenced by a relation, so its nodes should be kept in the index
                if ((keepIndexed) || (recBuilder.wayIndex.containsKey("" + myWay.getId()))) {
                    for (WayNode entry : myWay.getWayNodes()) {
                        recBuilder.nodeIndex.put("" + entry.getNodeId(), null);          //...initially with NULL geometry, to be replaced once nodes will be parsed
                    }
                }
            } else {                      //Parsing of this OSM way

                if (inNode) {
                    System.out.println("\nFinished parsing OSM nodes.");
                }
                //Mark position of the parser
                inNode = false;
                inWay = true;
                inRelation = false;
                numWays++;

                //Skip parsing if this way is filtered out or not referenced by other relations
                if ((!keepIndexed) && (!recBuilder.wayIndex.containsKey("" + myWay.getId()))) {
                    return;
                }
                wayTmp = new OSMWay();
                wayTmp.setID("" + myWay.getId());

                //Collect tags associated with this OSM element
                for (Tag myTag : myWay.getTags()) {
                    wayTmp.setTagKeyValue(myTag.getKey(), myChecker.removeIllegalChars(myTag.getValue()));
                }

                for (WayNode entry : myWay.getWayNodes()) {
                    if (recBuilder.nodeIndex.containsKey("" + entry.getNodeId())) {
                        Geometry geometry = recBuilder.nodeIndex.get("" + entry.getNodeId());     //get the geometry of the node with ID=entry
                        wayTmp.addNodeGeometry(geometry);                                         //add the node geometry in this way
                    } else {
                        System.out.println("Missing node " + entry.getNodeId() + " in referencing way " + wayTmp.getID());
                    }
                }
                Geometry geom = geometryFactory.buildGeometry(wayTmp.getNodeGeometries());

                //Check if the beginning and ending node are the same and the number of nodes are more than 3.
                //These nodes must be more than 3, because JTS does not allow construction of a linear ring with less than 3 points
                if ((closedRings2Polygons) && (wayTmp.getNodeGeometries().size() > 3) && wayTmp.getNodeGeometries().get(0).equals(wayTmp.getNodeGeometries().get(wayTmp.getNodeGeometries().size() - 1))) {
                    //Always construct a polygon when a linear ring is detected
                    LinearRing linear = geometryFactory.createLinearRing(geom.getCoordinates());
                    Polygon poly = new Polygon(linear, null, geometryFactory);
                    wayTmp.setGeometry(poly);

                    /*************************************************
                     //OPTION NOT USED: Construct a linear ring geometry when this feature is either a barrier or a road
                     if (!((wayTmp.getTagKeyValue().containsKey("barrier")) || wayTmp.getTagKeyValue().containsKey("highway"))){
                     //this is not a barrier nor a road, so construct a polygon geometry

                     LinearRing linear = geometryFactory.createLinearRing(geom.getCoordinates());
                     Polygon poly = new Polygon(linear, null, geometryFactory);
                     wayTmp.setGeometry(poly);
                     }
                     else {    //it is either a barrier or a road, so construct a linear ring geometry
                     LinearRing linear = geometryFactory.createLinearRing(geom.getCoordinates());
                     wayTmp.setGeometry(linear);
                     }
                     **************************************************/
                } else if (wayTmp.getNodeGeometries().size() > 1) {
                    //it is an open geometry with more than one nodes, make it linestring

                    LineString lineString = geometryFactory.createLineString(geom.getCoordinates());
                    wayTmp.setGeometry(lineString);
                } else {      //we assume that any other geometries are points
                    //some ways happen to have only one point. Construct a Point.
                    Point point = geometryFactory.createPoint(geom.getCoordinate());
                    wayTmp.setGeometry(point);
                }

                //Convert this entity
                if (keepIndexed) {
                    if (keepUnnamed) {
                        myConverter.parse(recBuilder.createOSMRecord(wayTmp), classification, reproject, targetSRID);
                    } else if (wayTmp.getTagKeyValue().containsKey("name")) {  //CAUTION! Only named entities will be transformed
                        myConverter.parse(recBuilder.createOSMRecord(wayTmp), classification, reproject, targetSRID);
                        numNamedEntities++;
                    }
                }

                if (recBuilder.wayIndex.containsKey(wayTmp.getID())) {
                    recBuilder.wayIndex.put(wayTmp.getID(), wayTmp.getGeometry());          //Keep a dictionary of way geometries, only for those referenced by OSM relations
                }
                wayTmp = null;
            }
        } else if ((!scanWays) && (entityContainer instanceof RelationContainer)) {               //Create a new OSM relation object and populate it with the appropriate values

            Relation myRelation = ((RelationContainer) entityContainer).getEntity();

            for (Tag myTag : myRelation.getTags()) {
                if ((tags == null) || (tags.contains(myTag.getKey()))) {            //CAUTION! Filter out any OSM elements not related to tags specified by the user
                    keepIndexed = true;                                             //In case of no tags specified for filtering, index all nodes
                    break;
                }
            }

            if (scanRelations) {
                //Either only filtered relations will be indexed or those referenced by other relations
                if ((keepIndexed) || (recBuilder.relationIndex.containsKey("" + myRelation.getId()))) {
                    for (RelationMember m : myRelation.getMembers()) {
                        if (m.getMemberType().name().equalsIgnoreCase("node")) {
                            recBuilder.nodeIndex.put("" + m.getMemberId(), null);
                        }      //This node is referenced by a relation; keep it in the index, and its geometry will be filled in when parsing the nodes
                        else if (m.getMemberType().name().equalsIgnoreCase("way")) {
                            recBuilder.wayIndex.put("" + m.getMemberId(), null);
                        }           //This way is referenced by a relation; keep it in the index, and its geometry will be filled in when parsing the ways
                        else if (m.getMemberType().name().equalsIgnoreCase("relation")) {
                            recBuilder.relationIndex.put("" + m.getMemberId(), null);               //This relation is referenced by another relation; keep it in the index, and its geometry will be filled in when parsing the relations
                            rescanRelations = true;                                                 //Relations need to be scanned once more, as they reference other relations
                        }
                    }
                }
            } else {    //Parsing of this OSM relation

                if (inWay) {
                    System.out.println("\nFinished parsing OSM ways.");
                }
                //Mark position of the parser
                inNode = false;
                inWay = false;
                inRelation = true;
                numRelations++;

                //Skip parsing if this relation is filtered out or not referenced by others
                if ((!keepIndexed) && (!recBuilder.relationIndex.containsKey("" + myRelation.getId()))) {
                    return;
                }

                relationTmp = new OSMRelation();
                relationTmp.setID("" + myRelation.getId());

                //Collect tags associated with this OSM element
                for (Tag myTag : myRelation.getTags()) {
                    relationTmp.setTagKeyValue(myTag.getKey(), myChecker.removeIllegalChars(myTag.getValue()));
                }

                //Collect all members of this relation
//					 System.out.println("Relation " + myRelation.getId() + " Number of members: " +  myRelation.getMembers().size());
                for (RelationMember m : myRelation.getMembers()) {
                    relationTmp.addMemberReference("" + m.getMemberId(), m.getMemberType().name(), m.getMemberRole());
                }
                OSMRecord rec = recBuilder.createOSMRecord(relationTmp);
                if (rec != null)                  //No records created for incomplete relations during the first pass
                {
                    //Convert entity
                    if (keepIndexed) {
                        if (keepUnnamed) {
                            myConverter.parse(rec, classification, reproject, targetSRID);
                        } else if (relationTmp.getTagKeyValue().containsKey("name")) {   //CAUTION! Only named entities will be transformed
                            myConverter.parse(rec, classification, reproject, targetSRID);
                            numNamedEntities++;
                        }
                    }

                    if (recBuilder.relationIndex.containsKey(relationTmp.getID())) {
                        recBuilder.relationIndex.put(relationTmp.getID(), rec.getGeometry());    //Keep a dictionary of relation geometries, only for those referenced by other OSM relations
                    }
                }

                relationTmp = null;
            }
        }

    }

    /**
     * Completes the parsing process.
     */
    @Override
    public void complete() {
    }

    /**
     * Closes the parsing process.
     */
    @Override
    public void close() {
    }


    /**
     * Applies transformation according to the configuration settings.
     */
    public void apply() {

        numNodes = 0;
        numWays = 0;
        numRelations = 0;
        numNamedEntities = 0;

        try {
            if (currentConfig.mode.contains("STREAM")) {
                //Mode STREAM: consume records and streamline them into a serialization file
                if (currentConfig.serialization == null) {  // Only CSV file will be emitted
                    myConverter = new OsmCsvConverter(currentConfig, myAssistant, outputFile);
                } else { // Transformation will emit an RDF file with triples and a CSV file with records
                    myConverter = new OsmRdfCsvStreamConverter(currentConfig, myAssistant, outputFile);
                }
                //Parse each OSM entity and streamline the resulting triples (including geometric and non-spatial attributes)
                parseDocument();

                //Finalize the output RDF file
                myConverter.store(outputFile);
            } else    //TODO: Implement method for handling transformation using RML mappings
            {
                System.out.println("Mode " + currentConfig.mode + " is currently not supported against OSM XML datasets.");
                throw new IllegalArgumentException(Constants.INCORRECT_SETTING);
            }

        } catch (Exception e) {
            LOGGER.error("");
        }

        System.out.println(myAssistant.getGMTime() + " Original OSM file contains: " + numNodes + " nodes, " + numWays + " ways, " + numRelations + " relations.");
        if (!keepUnnamed) {
            System.out.println(" In total, " + numNamedEntities + " entities had a name and only those were given as input to transformation.");
        } else {
            System.out.println();
        }
    }
}
