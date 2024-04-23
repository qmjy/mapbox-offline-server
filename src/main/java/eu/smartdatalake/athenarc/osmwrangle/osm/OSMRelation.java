/*
 * @(#) OSMRelation.java 	 version 2.0   27/6/2018
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

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class containing information about the OSM relations.
 * 
 * @author Nikos Karagiannakis
 * Modified: 7/9/2017 by Kostas Patroumpas
 * Last revised: 27/6/2018 by Kostas Patroumpas
 */
public class OSMRelation implements Serializable{
    
    private static final long serialVersionUID = 1L;    
    private String id;
    private Set<Integer> classIDs;
    private final Map<String, ImmutablePair<String, String>> memberReferences = new HashMap<>();
    private final Map<String, String> tags = new HashMap<>();
    
    public String getID(){
        return id;
    }
    
    public Map<String, ImmutablePair<String, String>> getMemberReferences(){
        return memberReferences;
    }
    
    public Set<Integer> getClassIDs(){
        return this.classIDs;
    }
    
    public Map<String, String> getTagKeyValue(){
        return tags;
    }
    
    public void setID(String id){
        this.id = id;
    }
    
    public void setClassIDs(Set<Integer> classIDs){
        this.classIDs = classIDs;
    }    
    
    public void addMemberReference(String memberReference, String type, String role){
        this.memberReferences.put(memberReference, new ImmutablePair<>(type, role));        //Type and role of this OSM element are stored as the value of this member reference
    }    
    
    public void setTagKeyValue(String tagKey, String tagValue){
        this.tags.put(tagKey, tagValue);
    }
}