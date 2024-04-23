/*
 * @(#) OSMRecord.java 	 version 2.0   24/2/2018
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
package eu.smartdatalake.athenarc.osmwrangle.osm;

import org.locationtech.jts.geom.Geometry;

import java.util.HashMap;
import java.util.Map;

/**
 * Class containing information about the OSM records (nodes, ways, or relations).
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 25/7/2017
 * Modified: 7/9/2017, added support for handling all OSM tags
 * Last modified: 24/2/2018
 */

public class OSMRecord {
    
    private String id;
	private String name;
	private String type;
	private String category;
    private Geometry geometry;
	private final Map<String, String> tags = new HashMap<>(); 
    
    //Attribute getters
	/**
	 * Provides the identifier of an OSM feature (node, way, or relation).
	 * @return The OSM identifier.
	 */
    public String getID(){
        return id;
    }

    /**
     * Provides the name of an OSM feature (node, way, or relation).
     * @return The OSM name.
     */
    public String getName(){
        return name;
    }
    
    /**
     * Provides the type of an OSM feature (node, way, or relation).
     * @return The OSM type.
     */
    public String getType(){
        return type;
    } 

    /**
     * Provides the category assigned to an OSM feature (node, way, or relation).
     * @return The category assigned according to OSM tags, as defined in a user-specified classification scheme.
     */
    public String getCategory(){
        return category;
    } 
    
    /**
     * Provides the geometry representation of the OSM feature.
     * @return The geometry of the OSM feature.
     */
    public Geometry getGeometry(){
        return this.geometry;
    }
    
    /**
     * Provides a dictionary with all OSM tags and their respective values for an OSM feature.
     * @return Key-value pairs of OSM tags.
     */
    public Map<String,String> getTagKeyValue(){
        return tags;
    }      
    
    //Attributes setters
    /**
     * Sets or updates the identifier of the OSM feature.
     * @param id  The identifier to be assigned.
     */
    public void setID(String id){
        this.id = id;
    }
    
    /**
     * Sets or updates the name of an OSM feature (node, way, or relation).
     * @param name  A string to be assigned as name.
     */
    public void setName(String name){
        this.name = name;
    }
    
    /**
     * Sets or updates the type of an OSM feature (node, way, or relation).
     * @param type  A string to be assigned as type.
     */
    public void setType(String type){
        this.type = type;
    }
   
    /**
     * Sets or updates the category assigned to an OSM feature (node, way, or relation).
     * @param category  A string to be assigned as category.
     */
    public void setCategory(String category){
        this.category = category;
    }
    
    /**
     * Sets or updates the geometry of an OSM feature (node, way, or relation).
     * @param geometry  The geometry representation to be assigned.
     */
    public void setGeometry(Geometry geometry){
        this.geometry = geometry;
    }
    
    /**
     * Sets or updates the value of a specific tag for an OSM feature (node, way, or relation).
     * @param tagKey  The name of the tag.
     * @param tagValue  The value to be assigned to this tag.
     */
    public void setTagKeyValue(String tagKey, String tagValue){
        this.tags.put(tagKey, tagValue);
    }
    
    /**
     * Sets or updates all tags associated with an OSM feature (node, way, or relation).
     * @param tags  Key-value pairs to be assigned as tags to the OSM feature.
     */
    public void setTags(Map<String, String> tags){
        this.tags.putAll(tags);
		this.tags.remove("name");   // Already kept separately
    }
}