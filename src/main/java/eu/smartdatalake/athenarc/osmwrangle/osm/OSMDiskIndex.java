/*
 * @(#) OSMDiskIndex.java 	 version 2.0   11/7/2018
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
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.mapdb.*;

import java.util.Map;

/**
 * Support for disk-based indexing of OSM elements.
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 15/6/2018
 * Modified: 20/6/2018 by Kostas Patroumpas; included methods for reading and writing geometries in the index
 * Modified: 2/7/2018 by Kostas Patroumpas; using thread-safe WKB reader/writer to avoid invalid geometry representations
 * Last modified by: Kostas Patroumpas, 11/7/2018
 */

public class OSMDiskIndex implements OSMIndex { 

	/**
	 * Thread-safe WKB reader
	 */
    static ThreadLocal<WKBReader> wkbReader   = new ThreadLocal<WKBReader>() {
        protected WKBReader initialValue() { return new WKBReader(); };
    };

    /**
     * Thread-safe WKB writer
     */
    static ThreadLocal<WKBWriter> wkbWriter   = new ThreadLocal<WKBWriter>() {
        protected WKBWriter initialValue() { return new WKBWriter(); };
    };
    
	private DB db;      
	private HTreeMap<String, byte[]> index;
	
	private WKBReader reader;
	private WKBWriter writer;
	
	byte[] empty = new byte[1];             //Special value to indicate empty geometries
	
	/**
	 * Constructor of this class.
	 * @param tmpDir  Directory that will hold the disk-based indices to be created; once transformation is complete, these files will be erased.
	 * @param dbName  Name of the file that will hold the created index.
	 */
    public OSMDiskIndex(String tmpDir, String dbName) {
    	
    	//Disk-based mapDB structures for indexing
        db = DBMaker
        		.fileDB(tmpDir +  "/" + dbName + ".db")
        		.executorEnable()
                .allocateStartSize(512 *1024*1024)	//512MB
                .allocateIncrement(64 * 1024*1024) 	// 64MB
                .fileMmapEnable()
                .fileMmapEnableIfSupported()        //Activate mmap files only if a 64bit platform is detected
                .fileMmapPreclearDisable()
                .cleanerHackEnable()                // Release resources when file is closed. May cause JVM crash if file is accessed after it was unmapped.
                .closeOnJvmShutdown()
                .fileDeleteAfterClose()             //Indices will be destroyed upon termination
                .make();  

        index = db.hashMap(dbName)
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.BYTE_ARRAY_NOSIZE)   //Geometries are stored in their WKB representation
                .createOrOpen();
        
        reader = wkbReader.get();          
        writer = wkbWriter.get();          
	}

    /**
     * Inserts (or updates) an entry into the index with the given key value and geometry.
     * @param k  The key value of the entry.
     * @param g  The geometry of the entry.
     */
    public void put(String k, Geometry g) {	  	
    	if ((g != null) && (!g.isEmpty()))
    		index.put(k, writer.write(g));                	//Encode geometry in its WKB representation
    	else
    		index.put(k, empty);
	}
    
    /**
     * Provides the geometry indexed under the given (unique) key value.
     * @param k  The key value to find.
     * @return  The geometry of the indexed entry with the specified key.
     */
    public Geometry get(String k) {
		try {
			if (!index.containsKey(k))
				return null;
			
			if ((index.get(k) != null) && (index.get(k).length > 1))
				return reader.read(index.get(k));           //Decode geometry from its WKB representation
			else
				return null;
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
	}
    
    /**
     * Erases all contents of the index and closes the respective file on disk.
     */
    public void clear() {
		index.clear();
		db.close();
	}

    /**
     * Provides the count of entries in the index.
     * @return  An integer value representing the total count.
     */
	public int size() {
		return index.size();
	}

	/**
	 * Checks whether the index contains an entry with the given key value.
	 * @param k  A key value to check with the index contents.
	 * @return True if there is an entry with that key; otherwise, False.
	 */
	public boolean containsKey(String k) {
		return index.containsKey(k);
	}

	/**
	 * Inserts a collection of geometries into the index.
	 * @param m  Collection of geometries with their keys (string values).
	 */
	public void putAll(Map<String, Geometry> m) {
		//Iterate over all entries in the given map
		for (Map.Entry<String, Geometry>item: m.entrySet())
			this.put(item.getKey(),item.getValue());	
	}
    
	/**
	 * Print the contents of the index to standard output.
	 */
	public void print() {
		for (Object key: index.keySet())
			System.out.println(key.toString());		
	}
}
