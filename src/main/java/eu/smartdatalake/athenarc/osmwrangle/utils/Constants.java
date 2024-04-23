/*
 * @(#) Constants.java 	 version 2.0   23/10/2019
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

/**
 * Constants utilized in the transformation and reverse transformation processes.
 *
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * initially implemented for geometry2rdf utility (source: https://github.com/boricles/geometry2rdf/tree/master/Geometry2RDF)
 * Modified by: Kostas Patroumpas, 8/2/2013; adjusted to TripleGeo functionality
 * Last modified: 23/10/2019
 */
public class Constants {

  //REPLACEMENT value strings
  /**
   * Default line separator
   */
  public static final String LINE_SEPARATOR = "\n";      
  
  /**
   * String representation of UTF-8 encoding
   */
  public static final String UTF_8 = "UTF-8";           
  
  /**
   * Default delimiter of the CSV file used for registering features in the SLIPO Registry
   */
  public static final String OUTPUT_CSV_DELIMITER = "|";          

  /**
   * Default header with the attribute names of the CSV file used for output features
   */
  public static final String OUTPUT_CSV_HEADER = "ID|NAME|CATEGORY|SUBCATEGORY|LON|LAT|SRID|WKT";  
  
  /**
   * Suffix to URIs for geometries of features
   */
  public static final String GEO_URI_SUFFIX = "/geom"; 
  
  /**
   * Default delimiter of the CSV file used for registering features in the SLIPO Registry
   */
  public static final String REGISTRY_CSV_DELIMITER = "|";          

  /**
   * Default header with the attribute names of the CSV file used for registering features in the SLIPO Registry
   */
  public static final String REGISTRY_CSV_HEADER = "URI" + "|" + "SOURCE_PROVIDER" + "|" + "SOURCE_POI_ID" + "|" + "POI_NAME" + "|" + "POI_CATEGORY" + "|" + "LONGITUDE" + "|" + "LATITUDE";          

  //INDEX with available connectors to several DBMS
  /**
   * Index key of MSAccess database connector
   */
  public static final int MSACCESS = 0;

  /**
   * Index key of MySQL database connector
   */
  public static final int MYSQL = 1;  
  
  /**
   * Index key of Oracle Spatial database connector
   */
  public static final int ORACLE = 2;      
  
  /**
   * Index key of PostGIS database connector
   */
  public static final int POSTGIS = 3;       
  
  /**
   * Index key of IBM DB2 database connector
   */
  public static final int DB2 = 4;        
  
  /**
   * Index key of Microsoft SQLServer database connector
   */
  public static final int SQLSERVER = 5;        
  
  /**
   * Index key of SpatiaLite database connector
   */
  public static final int SPATIALITE = 6;               

  /**
   * Index of available drivers for connections to DBMS
   */
  public static final String[] DBMS_DRIVERS =   //NOT USED: "sun.jdbc.odbc.JdbcOdbcDriver" for ODBC-JDBC bridge (obsolete)
    {"net.ucanaccess.jdbc.UcanaccessDriver" , "com.mysql.jdbc.Driver", "oracle.jdbc.driver.OracleDriver", 
     "org.postgresql.Driver", "com.ibm.db2.jcc.DB2Driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.sqlite.JDBC"};

  /**
   * Index of URLs used in JDBC connections with each DBMS
   */
  public static final String[] BASE_URL = {"jdbc:ucanaccess:", "jdbc:mysql:", "jdbc:oracle:thin:", "jdbc:postgresql:", "jdbc:db2:", "jdbc:sqlserver:", "jdbc:sqlite:"};

  
  //ALIASES for most common namespaces 
  /**
   * Namespace for GeoSPARQL ontology
   */
  public static final String NS_GEO = "http://www.opengis.net/ont/geosparql#";   

  /**
   * Namespace for GeoSPARQL spatial features
   */
  public static final String NS_SF =  "http://www.opengis.net/ont/sf#";                               
  
  /**
   * Namespace for GML ontology
   */
  public static final String NS_GML = "http://loki.cae.drexel.edu/~wbs/ontology/2004/09/ogc-gml#";    

  /**
   * Namespace for XML Schema
   */
  public static final String NS_XSD = "http://www.w3.org/2001/XMLSchema#";                            
  
  /**
   * Namespace for RDF Schema
   */
  public static final String NS_RDFS = "http://www.w3.org/2000/01/rdf-schema#";  

  /**
   * Legacy namespace for WGS84 Geoposition RDF vocabulary
   */
  public static final String NS_POS = "http://www.w3.org/2003/01/geo/wgs84_pos#";   

  /**
   * Legacy namespace for Virtuoso RDF geometries
   */
  public static final String NS_VIRT = "http://www.openlinksw.com/schemas/virtrdf#";                  

  /**
   * Namespace for Dublin Core Metadata Initiative terms
   */
  public static final String NS_DC = "http://purl.org/dc/terms/";                                     
  
  
  //ALIASES for most common tags and properties for RDF triples
  public static final String GEOMETRY = "Geometry";
  public static final String FEATURE = "Feature";
  public static final String LATITUDE = "lat";
  public static final String LONGITUDE = "long";
  public static final String WKT = "asWKT";
  public static final String WKTLiteral = "wktLiteral";
  
  
  //Strings appearing in user notifications and warnings
  public static final String COPYRIGHT = "*********************************************************************\n*                     OSMWrangle version 2.0                        *\n* Developed by the Information Management Systems Institute.        *\n* Copyright (C) 2013-2019 Athena Research Center, Greece.           *\n* This program comes with ABSOLUTELY NO WARRANTY.                   *\n* This is FREE software, distributed under GPL license.             *\n* You are welcome to redistribute it under certain conditions       *\n* as mentioned in the accompanying LICENSE file.                    *\n*********************************************************************\n";
  public static final String INCORRECT_CONFIG = "Incorrect number of arguments. A properties file with proper configuration settings is required.";
  public static final String INCORRECT_CLASSIFICATION = "Incorrect number of arguments. Please specify a classification file in YML or CSV format, and a boolean value indicating whether classification hierarchy is based on category names (TRUE) or their identifiers (FALSE).";
  public static final String INCORRECT_SETTING = "Incorrect or no value set for at least one parameter. Please specify a correct value in the configuration settings.";
  public static final String INCORRECT_DBMS = "Incorrect or no value set for the DBMS where input data is stored. Please specify a correct value in the configuration settings.";
  public static final String NO_REPROJECTION = "No reprojection to another coordinate reference system will take place.";
  public static final String WGS84_PROJECTION = "Input data is expected to be georeferenced in WGS84 (EPSG:4326).";
  
}
