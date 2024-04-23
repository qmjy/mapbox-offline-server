/*
 * @(#) OsmRecordBuilder.java	version 2.0   24/10/2018
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

import eu.smartdatalake.athenarc.osmwrangle.osm.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.linemerge.LineMerger;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Creates OSM record objects that contain all geospatial and thematic information from OSM elements (nodes, ways, relations).
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 19/4/2017
 * Modified: 7/9/2017; reorganized methods in order to be applicable to both XML and PBF input files from OpenStreetMap.
 * Modified: 24/10/2018; allowing transformation even in case that no filters (using OSM tags) have been specified over OSM features
 * Last modified by: Kostas Patroumpas, 24/10/2018
 */

public class OSMRecordBuilder {

	public static List<OSMFilter> filters;              //Parser for a file with filters for assigning categories to OSM features

	private GeometryFactory geometryFactory = new GeometryFactory();
	
	//Using either in-memory or on-disk dictionaries for referenced OSM elements
	public OSMIndex nodeIndex;        				//Global dictionary containing OSM IDs as keys and the corresponding geometries of OSMNode objects
	public OSMIndex wayIndex;         				//Global dictionary containing OSM IDs as keys and the corresponding geometries of OSMWay objects  
	public OSMIndex relationIndex;    				//Global dictionary containing OSM IDs as keys and the corresponding geometries of OSMRelation objects
	public List<OSMRelation> incompleteRelations;
	
	public OSMRecordBuilder(List<OSMFilter> tagFilters) {
		filters = tagFilters;
	}
	
	  /**
	   * Assign a category to a OSM feature (node, way, or relation) based on its tags.
	   * @param tags  Key-value pairs for OSM tags and their respective values for a given feature.
	   * @return  A category according to the classification scheme based on OSM tags.
	   */
	  private static String getCategory(Map<String, String> tags) {
		//Iterate over filters
		String cat = null;
		for (OSMFilter filter : filters) {
			cat = getCategoryRecursive(filter, tags, null);
			if (cat != null) {
				return cat;
			}
		}
		return null;
	  }



	  /**
	   * Recursively search in the classification hierarchy to match the given tag (key).
	   * @param filter  Correspondence of OSM tags into categories.
	   * @param tags  Key-value pairs for OSM tags and their respective values for a given feature.
	   * @param key  Tag at any level in the classification hierarchy.
	   * @return  The name of the corresponding category.
	   */
	  private static String getCategoryRecursive(OSMFilter filter, Map<String, String> tags, String key) {
			//Use key of parent rule or current
			if (filter.hasKey()) {
				key = filter.getKey();
			}

			//Check for key/value
			if (tags.containsKey(key))
				if (filter.hasValue() && !filter.getValue().equals(tags.get(key)))
					return null;
				else
					;             //Important in order to search recursively across the hierarchy of categories (tags)
			else
				return null;

			//If children have categories, those will be used
			for (OSMFilter child : filter.childs)
			{
				String cat = getCategoryRecursive(child, tags, key);
				if (cat != null) 
					return cat;
			}
			return filter.getCategory();
	  }

	
	/**
	 * Constructs an OSMRecord object from a parsed OSM node.
	 * @param n  A parsed OSM node.
	 * @return  An OSMRecord object as a record with specific attributes extracted from the OSM node.
	 */
    public OSMRecord createOSMRecord(OSMNode n) {
  
		OSMRecord rec = new OSMRecord();
	  	rec.setID("node/" + n.getID());
	  	rec.setType(n.getTagKeyValue().get("type"));
		rec.setName((n.getTagKeyValue().get("name") == null ? "" : n.getTagKeyValue().get("name")));
	  	rec.setGeometry(n.getGeometry());
	  	rec.setTags(n.getTagKeyValue());
	  	if (filters != null)
	  		rec.setCategory(getCategory(n.getTagKeyValue()));       //Search among the user-specified filters in order to assign a category to this OSM feature
	  	else
	  		rec.setCategory("node");                                //Assign this category in cae that no user-specified classification is available
	  	return rec;  	
    }
  
  
    /**
     * Constructs an OSMRecord object from a parsed OSM way.
     * @param w  A parsed OSM way.
     * @return  An OSMRecord object as a record with specific attributes extracted from the OSM way.
     */
    public OSMRecord createOSMRecord(OSMWay w) {

		OSMRecord rec = new OSMRecord();
	  	rec.setID("way/" + w.getID());
	  	rec.setType(w.getTagKeyValue().get("type"));
	  	rec.setName((w.getTagKeyValue().get("name") == null ? "" : w.getTagKeyValue().get("name")));
	  	rec.setGeometry(w.getGeometry());
	  	rec.setTags(w.getTagKeyValue());
	  	if (filters != null)
	  		rec.setCategory(getCategory(w.getTagKeyValue()));       //Search among the user-specified filters in order to assign a category to this OSM feature
	  	else
	  		rec.setCategory("way");                                 //Assign this category in cae that no user-specified classification is available
	  	return rec;
    }
  
  
    /**
     * Examines all OSM way objects and checks whether they can construct a new ring (closed polyline) or update an existing one.
     * @param ways  Array of all the OSM ways identified in the feature.
     * @param numWays Number of OSM ways identified.
     * @param rings  Array of all linear rings identified in the feature.
     * @param numRings  Number of linear rings identified.
     * @return  The amount of rings (internal or external, depending on call) identified in the given feature.
     */
    @SuppressWarnings({ "unchecked"})
    private int rearrangeRings(LineString[] ways, int numWays, LinearRing[] rings, int numRings) {

		  //First, concatenate any polylines with common endpoints
		  LineMerger merger = new LineMerger();
		    for (int i=0; i<numWays; i++)
		    	if (ways[i] != null)
		    		merger.add(ways[i]); 
		    
		  //Then, for each resulting polyline, create a ring if this is closed
		  Collection<LineString> mergedWays = merger.getMergedLineStrings();  
		
		  for (LineString curWay: mergedWays)  
			  if (curWay.isClosed())
			  {
				  rings[numRings] = geometryFactory.createLinearRing(curWay.getCoordinates());
			      numRings += 1;
			  }	
		 	
		  return numRings; 
    }

  
    /**
     * Constructs an OSMRecord object from a parsed OSM relation. 
     * CAUTION! Sometimes, this process may yield topologically invalid geometries, because of irregularities (e.g., self-intersections) tolerated by OSM!
     * @param r   A parsed OSM relation.
     * @return  An OSMRecord object as a record with specific attributes extracted from the OSM relation.
     */
    public OSMRecord createOSMRecord(OSMRelation r) {

	    boolean incomplete = false;                             //Marks an OSM relation as incomplete in order to re-parse it at the end of the process
  		OSMRecord rec = new OSMRecord();
    	rec.setID("relation/" + r.getID());
    	rec.setType(r.getTagKeyValue().get("type"));
		rec.setName((r.getTagKeyValue().get("name") == null ? "" : r.getTagKeyValue().get("name")));
    	rec.setTags(r.getTagKeyValue());
    	if (filters != null)
    		rec.setCategory(getCategory(r.getTagKeyValue()));   //Search among the user-specified filters in order to assign a category to this OSM feature
	  	else
	  		rec.setCategory("relation");                        //Assign this category in cae that no user-specified classification is available
    	
    	//Reconstruct geometry from relating with its elementary nodes and ways
    	GeometryFactory geometryFactory = new GeometryFactory();
    	
    	//Examine the member geometries of each relation and create a specific type of geometry: MultiLineString, MultiPolygon, or GeometryCollection
    	try {
			if (r.getTagKeyValue().get("type") != null)
			{
				if ((r.getTagKeyValue().get("type").equalsIgnoreCase("multilinestring")) || (r.getTagKeyValue().get("type").equalsIgnoreCase("route")))
				{   //Create a MultiLineString for OSM relation features that are either a 'multilinestring' or a 'route'
					LineString[] memberGeometries = new LineString[r.getMemberReferences().size()];
					int numMembers = 0;

					//Iterate through all members of this OSMRelation
					for (Map.Entry<String, ImmutablePair<String, String>> member : r.getMemberReferences().entrySet())     //Handle ways
    	    		{	
	    				Geometry tmpWay = wayIndex.get(member.getKey());
	    				if (tmpWay != null)
    	    			{
	    					//If this member is a polygon, then convert it into a linestring first
	    					if (tmpWay.getClass() == Polygon.class) {
	    						tmpWay = ((Polygon) tmpWay).getExteriorRing();
	    					}
	    					if (tmpWay.getClass() == org.locationtech.jts.geom.Point.class) {
	    						System.out.println("OSM element " + member.getKey() + " is a point! " + tmpWay.toText());
	    					}
	    					memberGeometries[numMembers] = (LineString) tmpWay;     //Reference to OSMWay geometry
	    					numMembers++;
    	    			}
    	    		}
					
					if (numMembers > 0) {
						LineString[] finalGeometries = new LineString[numMembers];
						System.arraycopy(memberGeometries, 0, finalGeometries, 0, numMembers);
    					rec.setGeometry(geometryFactory.createMultiLineString(finalGeometries));
					}
				}
				else if ((r.getTagKeyValue().get("type").equalsIgnoreCase("multipolygon")) || (r.getTagKeyValue().get("type").equalsIgnoreCase("boundary")))
				{   //Create a (Multi)Polygon for OSM relation features that are either a 'multipolygon' or a 'boundary'
					LinearRing[] outerRings = new LinearRing[r.getMemberReferences().size()];
					LinearRing[] innerRings = new LinearRing[r.getMemberReferences().size()];
					LineString[] outerWays = new LineString[r.getMemberReferences().size()];
					LineString[] innerWays = new LineString[r.getMemberReferences().size()];

					int numInnerRings = 0;
					int numOuterRings = 0;
					int numInnerWays = 0;
					int numOuterWays = 0;
					
					//Iterate through all members of this OSMRelation
					for (Map.Entry<String, ImmutablePair<String, String>> member : r.getMemberReferences().entrySet())   //Handle ways       
    	    		{	
						Geometry tmpWay = wayIndex.get(member.getKey());
    	    			if (tmpWay != null)
    	    			{
    	    				if (tmpWay.getClass() == Polygon.class)
    	    				{
    	    					if (member.getValue().getValue().equalsIgnoreCase("inner"))     
    		    				{ 
    	    						innerRings[numInnerRings] = (LinearRing) ((Polygon) tmpWay).getExteriorRing();        //This OSMWay geometry is a complete inner ring
    	    						numInnerRings++;		
    		    				}
    	    					else //if (member.getValue().getValue().equalsIgnoreCase("outer"))                                  //Outer may not always be specified for such OSM entity!
    	    					{
	    	    					outerRings[numOuterRings] = (LinearRing) ((Polygon) tmpWay).getExteriorRing();       //Reference to OSMWay geometry
			    					numOuterRings++;	
    	    					}
    	    				}
    	    				else if (tmpWay.getClass() == LineString.class)
    	    				{     	    				
    	    					if (((LineString) tmpWay).isClosed()) {
    	    						if (member.getValue().getValue().equalsIgnoreCase("inner")) 
    	    						{
    	    							//Cast this OSMWay geometry into a complete inner ring
    	    							innerRings[numInnerRings] = geometryFactory.createLinearRing(java.util.Arrays.copyOf(tmpWay.getCoordinates(), tmpWay.getCoordinates().length));       
        	    						numInnerRings++;
    	    						}
    	    						else    //if (member.getValue().getValue().equalsIgnoreCase("outer"))     //Outer may not always be explicitly specified for such OSM entity!
    	    						{
/*
    	    							if (tmpWay.getNumGeometries() < 4)    //Special handling of non-closed linestrings
    	    							{
    	    								Coordinate[] coords = java.util.Arrays.copyOf(tmpWay.getCoordinates(), tmpWay.getCoordinates().length+1);
    	    								coords[coords.length-1] = coords[0];   //Append another vertex that coincides with the first one
    	    								tmpWay = geometryFactory.createLinearRing(coords);
    	    								System.out.println(tmpWay.toString());
    	    							}
*/
    	    							//Cast this OSMWay geometry into a complete outer ring
    	    							outerRings[numOuterRings] = geometryFactory.createLinearRing(java.util.Arrays.copyOf(tmpWay.getCoordinates(), tmpWay.getCoordinates().length));
	    	    						numOuterRings++;
    	    						}
    	    					}
    	    					else
    	    					{
    	    						if (member.getValue().getValue().equalsIgnoreCase("inner")) 
    	    						{
    	    							innerWays[numInnerWays] = (LineString) tmpWay;          //This OSMWay geometry is only a part of an inner ring
        	    						numInnerWays++;
    	    						}
    	    						else //if (member.getValue().getValue().equalsIgnoreCase("outer"))     //Outer may not always be explicitly specified for such OSM entity!
    	    						{
	    	    						outerWays[numOuterWays] = (LineString) tmpWay;          //This OSMWay geometry is only a part of an outer ring
	    	    						numOuterWays++;
    	    						}						
    	    					}
		    				}
    	    				else if (tmpWay.getClass() == LinearRing.class)
    	    				{
    	    					if (member.getValue().getValue().equalsIgnoreCase("inner"))
    		    				{ 
    	    						innerRings[numInnerRings] = (LinearRing) tmpWay;        //This OSMWay geometry is an inner ring
    	    						numInnerRings++;
    	    						
    		    				}
    	    					else //if (member.getValue().getValue().equalsIgnoreCase("outer"))     //Outer may not always be explicitly specified for such OSM entity!
    	    					{
	    	    					outerRings[numOuterRings] = (LinearRing) tmpWay;        //Reference to OSMWay geometry
			    					numOuterRings++;
    	    					}
    	    				}
//    	    				else
//   	    					System.out.println(rec.getID() + ": Geometry is neither a LINESTRING nor a POLYGON! " + tmpWay.toString());
    	    			}
    	    		}
					
					//Update INNER rings by merging their constituent linestrings
					numInnerRings = rearrangeRings(innerWays, numInnerWays, innerRings, numInnerRings);
					//Update OUTER rings by merging their constituent linestrings
					numOuterRings = rearrangeRings(outerWays, numOuterWays, outerRings, numOuterRings);
					
					//A polygon, possibly with hole(s)
					if (numOuterRings == 1)
					{
						LinearRing[] innerHoles = new LinearRing[numInnerRings];
						if (numInnerRings > 0)
							System.arraycopy(innerRings, 0, innerHoles, 0, numInnerRings);
						else
							innerHoles = null;
						Polygon geomPolygon = geometryFactory.createPolygon(outerRings[0], innerHoles);
						rec.setGeometry(geomPolygon);
					}
					else              //A MultiPolygon consisting of multiple polygons (possibly with holes)
					{
						Polygon[] polygons = new Polygon[outerRings.length];
						int numMembers = 0;
				
						//Iterate through all constituent outer rings in order to create a number of polygons for this composite geometry
						for (int i=0; i<numOuterRings; i++)
							if (outerRings[i] != null)
							{
								//Identify if an inner ring is within an outer one
								LinearRing[] innerHoles = new LinearRing[numInnerRings];
								int k = 0;
								for (int j=0; j<numInnerRings; j++)
									if ((geometryFactory.createPolygon(outerRings[i],null)).contains(geometryFactory.createPolygon(innerRings[j],null)))
									{
										innerHoles[k] = innerRings[j];	
										k++;
									}
								
								//If at least one hole if found, create the respective polygon accordingly
								LinearRing[] finalHoles = new LinearRing[k];
								if (k > 0)
									System.arraycopy(innerHoles, 0, finalHoles, 0, k);
								else
									finalHoles = null;
							
								polygons[numMembers] = geometryFactory.createPolygon(outerRings[i], finalHoles);
								numMembers++;
							}
						Polygon[] finalPolygons = new Polygon[numMembers];
						System.arraycopy(polygons, 0, finalPolygons, 0, numMembers);
						MultiPolygon geomMultiPolygon = geometryFactory.createMultiPolygon(finalPolygons);
    					rec.setGeometry(geomMultiPolygon);
					}				
				}
			}
			
			if ((rec.getGeometry() == null) || (rec.getGeometry().isEmpty()))  //For any other type of OSM relations, create a geometry collection
			{
				Geometry[] memberGeometries = new Geometry[r.getMemberReferences().size()];
				int numMembers = 0;
				//Iterate through all members of this OSMRelation
				for (Map.Entry<String, ImmutablePair<String, String>> member : r.getMemberReferences().entrySet())    
	    		{	
					String k = member.getKey();
	    			if ((member.getValue().getKey().equalsIgnoreCase("way")) && (wayIndex.get(k) != null))              //Handle ways
	    			{
		    				memberGeometries[numMembers] = wayIndex.get(k);     //Reference to OSMWay geometry
		    				numMembers++;
	    			}
	    			else if ((member.getValue().getKey().equalsIgnoreCase("node")) && (nodeIndex.get(k) != null))     	//Handle nodes
	    			{	
							memberGeometries[numMembers] = nodeIndex.get(k);    //Reference to OSMNode geometry
		    				numMembers++;
	    			}
	    			else if ((member.getValue().getKey().equalsIgnoreCase("relation")) && (relationIndex.get(k) != null))     	//Handle relations
	    			{	
							memberGeometries[numMembers] = relationIndex.get(k);    //Reference to OSMRelation geometry
		    				numMembers++;
	    			}	    			
	    			else                                           //Missing constituent geometries
	    			{
//	    				System.out.println("There is no OSM element indexed with id: " + k);
	    				if (!incompleteRelations.contains(r))      //Add this relation when it is first encountered in the parsing
	    				{
	    					incompleteRelations.add(r);
	    					incomplete = true;                      //At least one constituent geometry is missing
	    				}
	    			}
	    		}
				 
				if (incomplete)                      //Incomplete relations will be given a second chance once the XML file is parsed in its entirety
					return null;
					//rec.setGeometry(null);
				else if (numMembers == 1)            //In case that this relation consists of one member only, just replicate the original geometry
					rec.setGeometry(memberGeometries[0]);
				else if (numMembers > 1)        //Otherwise, create a geometry collection
				{
    				GeometryCollection geomCollection = null;
    				geomCollection = geometryFactory.createGeometryCollection(java.util.Arrays.copyOfRange(memberGeometries, 0, numMembers));
    				rec.setGeometry(geomCollection);
	    		}
			}

    	} catch (Exception e) {
    		System.out.println("PROBLEM at " + rec.getID() + ". REASON: " + e.toString());
//			e.printStackTrace();
		}
/*
        //Diagnostics regarding null or invalid geometries
		if (rec.getGeometry() == null)
			System.out.print("Geometry is null for OSM id = " + rec.getID() + ". ");
		else if (!rec.getGeometry().isValid())
		{
			//System.out.println(rec.getID() + ";" + rec.getGeometry().toString());		
			System.out.println("Geometry of type " + rec.getGeometry().getGeometryType() + " is extracted, but it is not valid for OSM id = " + rec.getID() + ". ");
			//rec.setGeometry(myAssistant.geomValidate(rec.getGeometry()));    //Use a validate geometry; only applicable for polygon and multipolygon features
		}
*/   	
    	return rec;
    }
 
}
