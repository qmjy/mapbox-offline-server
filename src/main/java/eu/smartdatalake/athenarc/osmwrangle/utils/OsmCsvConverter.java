/*
 * @(#) OsmCsvConverter.java 	 version 2.0   5/12/2019
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
package eu.smartdatalake.athenarc.osmwrangle.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.smartdatalake.athenarc.osmwrangle.osm.OSMRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.graph.Triple;
import org.geotools.api.referencing.operation.MathTransform;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;


/**
 * Provides a set of a streaming OSM records in memory that can be readily serialized into a CSV file.
 * CAUTION! This version also emits a .CSV output file that includes all tags identified in OSM (XML or PBF) files.
 * DO NOT USE this version for transformation with other file formats.
 *
 * @author Kostas Patroumpas
 * @version 1.9
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 26/9/2018
 * Modified by: Kostas Patroumpas, 27/9/2018
 * Last modified: 11/7/2019
 */

public class OsmCsvConverter implements Converter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OsmCsvConverter.class);
    private static Configuration currentConfig;

    private Assistant myAssistant;                            //Performs auxiliary operations (geometry transformations, auto-generation of UUIDs, etc.)

    String DELIMITER = "|";

    //Used in performance metrics
    private long t_start;
    private long dt;
    private int numRec;            //Number of entities (records) in input dataset
    private int rejectedRec;       //Number of rejected entities (records) from input dataset after filtering
    private int numTriples;

    public Envelope mbr;          //Minimum Bounding Rectangle (in WGS84) of all geometries handled during a given transformation process

    private BufferedWriter csvWriter = null;

    Map<String, Integer> attrStatistics;   //Statistics for each attribute

    ObjectMapper mapperObj;

    //Attribute Map
    private final HashMap<String, String> tagMap = new HashMap<>();
    private final ArrayList<String> cols = new ArrayList<String>();

    public void ReadAttrMappingFile(Configuration config) {

        //Read Mapping Config File for extra thematic columns
        Properties properties = new Properties();
        try {
            properties.load(OsmCsvConverter.class.getResourceAsStream(config.mapping_file));

            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                cols.add(entry.getKey().toString());
                String[] value_parts = entry.getValue().toString().replace("\"", "").trim().split(",");

                for (String valuePart : value_parts) {
                    tagMap.put(valuePart.trim(), entry.getKey().toString());
                }
            }

        } catch (IOException io) {
            System.out.println(Level.WARNING + " Problems loading configuration file: " + io);
        }
    }

    /*
     * Formats a String escaping some special characters like: \n, \r, |
     * */
    public String formatString(String str) {
        return str.replaceAll("\\r\\n|\\r|\\n", " ").replace("|", ";").trim();
    }

    /**
     * Constructs a OsmCsvConverter object that will conduct transformation at STREAM mode.
     *
     * @param config     User-specified configuration for the transformation process.
     * @param outputFile Output file that will collect resulting triples.
     */
    public OsmCsvConverter(Configuration config, Assistant assist, String outputFile) {

        super();

        currentConfig = config;       //Configuration parameters as set up by the various conversion utilities (CSV, SHP, DB, etc.)

        myAssistant = assist;
        attrStatistics = new HashMap<String, Integer>();

        //Initialize MBR of transformed geometries
        mbr = new Envelope();
        mbr.init();

        mapperObj = new ObjectMapper();   //CAUTION! Only used in issuing tags for .CSV output

        //Read Mapping File and build Collection.
        ReadAttrMappingFile(currentConfig);

        //Initialize performance metrics
        t_start = System.currentTimeMillis();
        dt = 0;
        numRec = 0;
        rejectedRec = 0;
        numTriples = 0;

        //Specify the output .CSV file that will collect the resulting tuples after extraction from OSM
        try {
            csvWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(FilenameUtils.removeExtension(outputFile) + ".csv"), StandardCharsets.UTF_8));
            csvWriter.write(Constants.OUTPUT_CSV_HEADER + "|" + String.join("|", cols) + "|OTHER_TAGS");   //Custom header specified in Constants
            csvWriter.newLine();
        } catch (Exception e) {
            LOGGER.error("Output CSV file not specified correctly.");
        }
    }


    /**
     * Provides triples resulted after applying transformation against a single input feature or a small batch of features.
     */
    public List<Triple> getTriples() {

        return null;
    }


    /**
     * Parses a single OSM record and streamlines the resulting records (including geometric and non-spatial attributes).
     * Applicable in STREAM transformation mode.
     * Input provided as an individual record. This method is used for input from OpenStreetMap XML/PBF files.
     *
     * @param rs         Representation of an OSM record with attributes extracted from an OSM element (node, way, or relation).
     * @param classific  Instantiation of the classification scheme that assigns categories to input features.
     * @param reproject  CRS transformation parameters to be used in reprojecting a geometry to a target SRID (EPSG code).
     * @param targetSRID Spatial reference system (EPSG code) of geometries in the output RDF triples.
     */
    public void parse(OSMRecord rs, Classification classific, MathTransform reproject, int targetSRID) {
        try {
            ++numRec;
            //CAUTION! On-the-fly generation of a UUID for this feature, giving as seed the data source and the identifier of that feature
            //String uuid = myAssistant.getUUID(currentConfig.featureSource, rs.getID()).toString();

            //CAUTION! These variables are only used when also creating a .CSV file with all OSM attributes
            double lon = 0;
            double lat = 0;
            String category = Constants.REGISTRY_CSV_DELIMITER;

            //Parse geometric representation
            String wkt = null;
            if ((rs.getGeometry() != null) && (!rs.getGeometry().isEmpty())) {
                lon = myAssistant.getLongitude(rs.getGeometry());    //CAUTION! These coordinate values only used for .CSV output
                lat = myAssistant.getLatitude(rs.getGeometry());

                wkt = rs.getGeometry().toText();       //Get WKT representation
                if (wkt != null) {
                    //Apply spatial filtering (if specified by user)
                    if (!myAssistant.filterContains(wkt)) {
                        rejectedRec++;
                        return;
                    }

                    //Update spatial extent (MBR) of the transformed data with this geometry
                    updateMBR(rs.getGeometry());

                    //CRS transformation
                    if (reproject != null)
                        wkt = myAssistant.wktTransform(wkt, reproject);     //Get transformed WKT representation
//				    else	
//				      	myAssistant.WKT2Geometry(wkt);                      //This is done only for updating the MBR of all geometries
                }
            } else {
                rejectedRec++;
//				System.out.println(rs.getID() + " has null geometry.");
                return;
            }

            //Tags to be processed as attribute values
            Map tags = rs.getTagKeyValue();
            Map<String, String> attrValues = new HashMap<String, String>(rs.getTagKeyValue());

            //Include standard attributes for OSM identifier, name, and type
            attrValues.put("osm_id", rs.getID());
            attrValues.put("name", rs.getName());
            attrValues.put("type", rs.getType());

            //Skip transformation of any features filtered out by the logical expression over thematic attributes
            if (myAssistant.filterThematic(attrValues)) {
                rejectedRec++;
                return;
            }

            //Include identified category in these tags as an extra attribute
            if (rs.getCategory() != null) {
                //CAUTION! Only used for .CSV output
                category = (rs.getCategory().contains("_")) ? rs.getCategory().replace("_", Constants.REGISTRY_CSV_DELIMITER) : rs.getCategory() + Constants.REGISTRY_CSV_DELIMITER;

                if (currentConfig.attrCategory != null)                //Attribute to be used in the Registry as well
                    attrValues.put(currentConfig.attrCategory, rs.getCategory());
                else
                    attrValues.put("OSM_Category", rs.getCategory());  //Ad-hoc name for this extra attribute
            } else {
                rejectedRec++;
                return;          //CAUTION! Do not proceed to transform unless this feature complies with the filtering tags specified by the user
            }

            //Process all tags and distinguish attribute values to export to CSV
            ArrayList<String> outArr = new ArrayList<String>();
            outArr.add(rs.getID());
            outArr.add(rs.getName());
            outArr.add(category);
            outArr.add(Double.toString(lon));
            outArr.add(Double.toString(lat));
            outArr.add(Integer.toString(targetSRID));
            outArr.add(rs.getGeometry().toText());

            // Update statistics for names
            if (rs.getName() != null)
                updateStatistics("NAME");

            Map tagMapRec = new HashMap<String, String>();
            for (int i = 0; i < cols.size(); i++) {
                tagMapRec.put(cols.get(i), "");
            }

            for (Map.Entry<String, String> entry : tagMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                String tagFieldValue = tags.getOrDefault(key, "").toString();
                String existingValue = tagMapRec.get(value).toString();

                if (existingValue == "") {
                    tagMapRec.put(value, tagFieldValue);
                } else {
                    if (tagFieldValue != "")
                        tagMapRec.put(value, existingValue + ";" + tagFieldValue);
                }

                tags.remove(key);
            }

            for (int i = 0; i < cols.size(); i++) {
                outArr.add(formatString(tagMapRec.get(cols.get(i)).toString()));
                // Update statistics
                if (tagMapRec.get(cols.get(i)) != "")
                    updateStatistics(cols.get(i));
            }

            String othertags = formatString(mapperObj.writeValueAsString(rs.getTagKeyValue()));
            outArr.add(othertags);

            csvWriter.write(String.join("|", outArr));
            csvWriter.newLine();

            //Periodically, collect RDF triples resulting from this batch and dump results into output file
            if (numRec % currentConfig.batch_size == 0) {
                myAssistant.notifyProgress(numRec);
            }

        } catch (Exception e) {
            System.out.println("Problem at element with OSM id: " + rs.getID() + ". Excluded from transformation.");
        }

    }


    /**
     * Update statistics (currently only a counter) of values transformed for a particular attribute
     *
     * @param attrKey The name of the attribute
     */
    private void updateStatistics(String attrKey) {

        if ((attrStatistics.get(attrKey)) == null)
            attrStatistics.put(attrKey, 1);                                  //First occurrence of this attribute
        else
            attrStatistics.replace(attrKey, attrStatistics.get(attrKey) + 1);  //Update count of NOT NULL values for this attribute
    }

    /**
     * Updates the MBR of the geographic dataset as this is being transformed.
     *
     * @param g Geometry that will be checked for possible expansion of the MBR of all features processed thus far
     */
    public void updateMBR(Geometry g) {

        Envelope env = g.getEnvelopeInternal();          //MBR of the given geometry
        if ((mbr == null) || (mbr.isNull()))
            mbr = env;
        else if (!mbr.contains(env))
            mbr.expandToInclude(env);                    //Expand MBR is the given geometry does not fit in
    }

    /**
     * Provides the MBR of the geographic dataset that has been transformed.
     *
     * @return The MBR of the transformed geometries.
     */
    public Envelope getMBR() {
        if ((mbr == null) || (mbr.isNull()))
            return null;
        return mbr;
    }

    /**
     * Finalizes storage of resulting tuples into a file.
     *
     * @param outputFile Path to the output file that collects RDF triples.
     */
    public void store(String outputFile) {
        //******************************************************************
        //Close the file that will collect all CSV tuples
        try {
            if (csvWriter != null)
                csvWriter.close();
        } catch (IOException e) {
            LOGGER.error("An error occurred during creation of the output CSV file.");
        }
        //******************************************************************

        //Measure execution time and issue statistics on the entire process
        dt = System.currentTimeMillis() - t_start;
        myAssistant.reportStatistics(dt, numRec, rejectedRec, numTriples, "CSV", attrStatistics, getMBR(), currentConfig.mode, currentConfig.targetCRS, FilenameUtils.removeExtension(outputFile) + ".csv", 0);
    }

}
