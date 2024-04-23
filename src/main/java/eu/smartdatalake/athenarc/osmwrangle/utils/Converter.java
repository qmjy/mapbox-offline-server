/*
 * @(#) Converter.java 	 version 2.0  11/7/2019
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

import eu.smartdatalake.athenarc.osmwrangle.osm.OSMRecord;
import org.apache.jena.graph.Triple;
import org.geotools.api.referencing.operation.MathTransform;

import java.util.List;

/**
 * Conversion Interface for TripleGeo used in transformation of spatial features (including their non-spatial attributes) into RDF triples with various serializations.
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 16/2/2013
 * Last modified: 11/7/2019
 */
public interface Converter {
	
	/**
	 * Parses a single OSM record and creates the resulting triples (including geometric and non-spatial attributes)
	 * @param rs  Representation of an OSM record with attributes extracted from an OSM element (node, way, or relation).
	 * @param classific  Instantiation of the classification scheme that assigns categories to input features.
	 * @param reproject  CRS transformation parameters to be used in reprojecting a geometry to a target SRID (EPSG code).
	 * @param targetSRID  Spatial reference system (EPSG code) of geometries in the output RDF triples.
	 */
	public void parse(OSMRecord rs, Classification classific, MathTransform reproject, int targetSRID);
	
	/**
	 * Stores resulting tuples into a file.	
	 * @param outputFile  Path to the output file that collects RDF triples.
	 */
	public void store(String outputFile);

	/**
	 * Provides triples resulted after applying transformation in STREAM mode.
	 * @return A collection of RDF triples.
	 */
    public List<Triple> getTriples();
		
}
