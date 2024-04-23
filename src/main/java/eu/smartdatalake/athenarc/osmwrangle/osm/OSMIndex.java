/*
 * @(#) OSMIndex.java 	 version 2.0   11/7/2018
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

import java.util.Map;

/**
 * Interface for indexing of OSM elements.
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 20/6/2018
 * Last modified: 11/7/2018
 */

public interface OSMIndex { 

	/**
	 * Inserts (or updates) an entry into the index with the given key value and geometry.
     * @param k  The key value of the entry.
     * @param g  The geometry of the entry.
	 */
    public void put(String k, Geometry g);
    
    /**
     * Provides the geometry indexed under the given (unique) key value.
     * @param k  The key value to find.
     * @return  The geometry of the indexed entry with the specified key.
     */
    public Geometry get(String k);
    
    /**
     * Erases all contents of the index.
     */
    public void clear();
    
    /**
     * Checks whether the index contains an entry with the given key value.
	 * @param k  A key value to check with the index contents.
	 * @return True if there is an entry with that key; otherwise, False.
     */
    public boolean containsKey(String k);
    
    /**
     * Inserts a collection of geometries into the index.
	 * @param m  Collection of geometries with their keys (string values).
     */
    public void putAll(Map<String, Geometry> m);
    
    /**
     * Provides the count of entries in the index.
     * @return  An integer value representing the total count.
     */
    public int size();
    
    /**
     * Print the contents of the index to standard output.
     */
	public void print();
}
