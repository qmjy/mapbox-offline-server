/*
 * @(#) OSMWay.java 	 version 2.0   24/2/2018
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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import java.io.Serializable;
import java.util.*;


/**
 * Class containing information about the OSM ways.
 * 
 * @author Nikos Karagiannakis
 * Modified by: Kostas Patroumpas 24/2/2018; changed indexVector representation
 */

public class OSMWay implements Serializable{
    
    private static final long serialVersionUID = 1L;   
    private String id;   
    private int classID;
    private Set<Integer> classIDs;   
    private final List<String> nodeReferences = new ArrayList<String>();     //node references  //made final
    private final List<Geometry> nodeGeometries = new ArrayList<Geometry>(); //nodeGeometries   //made final
    private Coordinate[] coordinateList;    
    private final Map<String, String> tags = new HashMap<>();      
    private Geometry geometry;
    private TreeMap<Integer,Double> indexVector = new TreeMap<>(); 
    
    //Attribute getters 
    public String getID(){
        return id;
    } 
    
    public List<Geometry> getNodeGeometries(){
        return nodeGeometries;
    }
    
    public Coordinate[] getCoordinateList(){       
        coordinateList =  (Coordinate[]) nodeGeometries.toArray();
        return coordinateList;
    }
    
    public Geometry getGeometry(){
        return geometry;
    }   
    
    public List<String> getNodeReferences(){
        return nodeReferences;
    }
    
    public int getNumberOfNodes(){
        return nodeReferences.size();
    }
    
    public Map<String, String> getTagKeyValue(){
        return tags;
    }
    
    public int getClassID(){
        return classID;
    }
    
    public Set<Integer> getClassIDs(){
        return classIDs;
    }
      
    public TreeMap<Integer, Double> getIndexVector(){
        return indexVector;
    }
    
    //Attribute setters
    public void setID(String id){
        this.id = id;
    }
    
    public void setTagKeyValue(String tagKey, String tagValue){
        this.tags.put(tagKey, tagValue);
    }
    
    public void addNodeReference(String nodeReference){
        nodeReferences.add(nodeReference);
    }
    
    public void addNodeGeometry(Geometry geometry){
        nodeGeometries.add(geometry);
    }
    
    public void setGeometry(Geometry geometry){       
        this.geometry = geometry;
    }  
    
    public void setClassID(int classID){
        this.classID = classID;
    }

    public void setClassIDs(Set<Integer> classIDs){
        this.classIDs = classIDs;
    }  
    
    public void setIndexVector(TreeMap<Integer, Double> indexVector){
        this.indexVector = indexVector;
    }
    
}