/*
 * @(#) OSMNode.java 	 version 2.0   24/2/2018
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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Class containing information about the OSM nodes.
 * @author Nikos Karagiannakis
 */

public class OSMNode implements Serializable{
    
    private static final long serialVersionUID = 1L;
    private String id;
    private String lang;
    private String action; //e.g modify
    private String visible; 
    private Geometry geometry;
    private String timestamp;
    private String uid;
    private String user;
    private String version;
    private String changeset;
    private final Map<String, String> tags = new HashMap<>(); 
    
    //Attribute getters
    public String getID(){
        return id;
    }
    
    public String getlang(){
        return lang;
    }

    public String getAction(){
        return action;
    }
    
    public String getVisible(){
        return visible;
    } 

    public Geometry getGeometry(){
        return this.geometry;
    }
    
    public String getTimestamp(){
        return timestamp;
    }
    
    public String getUid(){
        return uid;
    }    
    
    public String getUser(){
        return user;
    }
 
    public String getVersion(){
        return version;
    }    

    public String getChangeset(){
        return changeset;
    }    
    
    public Map<String,String> getTagKeyValue(){
        return tags;
    }      
    
    //Attribute setters
    public void setID(String id){
        this.id = id;
    }
    
    public void setLang(String lang){
        this.lang = lang;
    }
    
    public void setAction(String action){
        this.action = action;
    }
    
    public void setVisible(String visible){
        this.visible = visible;
    }
    
    public void setGeometry(Geometry geometry){
        this.geometry = geometry;
    }
    
    public void setTimestamp(String timestamp){
        this.timestamp = timestamp;
    }
    
    public void setUid(String uid){
        this.uid = uid;
    }    
    
    public void setUser(String user){
        this.user = user;
    }

    public void setVersion(String version){
        this.version = version;
    }    
 
    public void setChangeset(String changeset){
        this.changeset = changeset;
    }    
    
    public void setTagKeyValue(String tagKey, String tagValue){
        this.tags.put(tagKey, tagValue);
    }
    
}