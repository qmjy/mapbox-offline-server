/*
 * @(#) Classification.java 	 version 2.0  25/10/2019
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

import io.github.qmjy.mapserver.service.AsyncService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for a (possibly multi-tier, i.e., hierarchical) classification scheme (category/subcategory/...).
 * ASSUMPTION: Each category (at any level) must have a unique name, which is being used as its key in the derived dictionary.
 *
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 7/11/2017
 * Modified: 1/2/2018, included auto-generation of universally unique identifiers (UUID) to be used in the URIs of categories
 * Modified: 7/2/2018, supported export of classification scheme into RDF triples (modes: GRAPH, RML)
 * Modified: 15/2/2018; distinguishing whether the classification scheme is based on identifiers or names of categories
 * Modified: 2/5/2018; supported export of classification scheme into RDF triples in STREAM mode
 * Modified: 11/12/2018; added mapping to an embedded category using a default classification scheme
 * Modified: 23/10/2019; also report the number of tiers in the classification hierarchy
 * Last modified by: Kostas Patroumpas, 25/10/2019
 */

public class Classification {
    private static final Logger LOGGER = LoggerFactory.getLogger(Classification.class);
    Converter myConverter;                        //Instantiation of Converter class used in the transformation of a classification scheme to RDF
    Assistant myAssistant;
    EmbeddedClassifier myEmbeddedClassifier;
    private String outputFile;                    //Output RDF file
    private Configuration currentConfig;          //User-specified configuration settings
    private boolean classifyByName;               //Whether data features specify their category based on its identifier in the classification scheme (false) or the actual name of the category (true).
    private int numTiers;  //Number of tiers (i.e. levels in the hierarchy) identified in the given classification scheme

    //CAUTION! Hard constraints in file specifications of classification schemes
    private static final int MAX_LEVELS = 10;     //Maximum depth (number of levels) in classification hierarchy
    private String splitter = "#";                //Character used to separate category name from its identifier in YML specifications
    private char indent = ' ';                    //Character used for indentation in YML file: a PAIR of such same characters is used to mark levels in the hierarchy

    /**
     * Dictionary for all categories and their parents; their name is used as the key in searching.
     */
    public Map<String, Category> categories = new HashMap<String, Category>();

    /**
     * Auxiliary dictionary for searching the artificial UUID (value) of a given original category identifier (key).
     */
    public Map<String, String> categoryUUIDs = new HashMap<String, String>();


    /**
     * Constructor of the classification hierarchy representation used for testing its validity
     *
     * @param classFile    Input file (CSV or YML) containing the user-specified classification scheme.
     * @param classifyFlag Boolean value: True, if the actual name of the category is used in the hierarchy; False, if identifiers are used instead.
     */
    public Classification(String classFile, boolean classifyFlag) {

        myAssistant = new Assistant();
        myEmbeddedClassifier = new EmbeddedClassifier();
        classifyByName = classifyFlag;
        numTiers = 0;

        //Depending on the type of the file containing hierarchy of categories, call the respective parser
        if (classFile.endsWith(".yml"))
            this.parseYMLFile(classFile);    //Parse the YML file
        else if (classFile.endsWith(".csv"))
            this.parseCSVFile(classFile);    //Parse the CSV file
        else {
            System.err.println("ERROR: Valid classification hierarchies must be stored in YML or CSV files.");
            System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
        }

    }


    /**
     * Constructor of the classification hierarchy representation to be used in transformation
     *
     * @param config    User-specified configuration for the transformation process.
     * @param classFile Input file (CSV or YML) containing the user-specified classification scheme.
     * @param outFile   Output file containing the RDF triples resulted from transformation of the classification scheme.
     */
    public Classification(Configuration config, String classFile, String outFile) {

        myAssistant = new Assistant();
        myEmbeddedClassifier = new EmbeddedClassifier();
        currentConfig = config;
        outputFile = outFile;     //Path to the (YML or CSV) file where classification hierarchy is stored; this path must be included in the configuration settings
        classifyByName = currentConfig.classifyByName;   //Assign value from the user-specified setting
        numTiers = 0;

        //Depending on the type of the file containing hierarchy of categories, call the respective parser
        if (classFile.endsWith(".yml"))
            this.parseYMLFile(classFile);    //Parse the YML file
        else if (classFile.endsWith(".csv"))
            this.parseCSVFile(classFile);    //Parse the CSV file
        else {
            System.err.println("ERROR: Valid classification hierarchies must be stored in YML or CSV files.");
            System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
        }

        //Once the classification scheme has been constructed, apply transformation to RDF
        if (currentConfig.mode.contains("STREAM")) {    //Mode STREAM: Apply a custom mapping specified in YML in order to produce triples
            myConverter = new OsmRdfCsvStreamConverter(currentConfig, myAssistant, outputFile);
            executeParser4Stream();
        }
    }


    /**
     * Parses input YML file containing the classification hierarchy
     *
     * @param classificationFile Path to YML file specifying the classification scheme.
     *                           ASSUMPTION: Each line in the YML file corresponds to a category; levels are marked with a number of indentation characters at the beginning of each line; no indentation signifies a top-tier category.
     */
    public void parseYMLFile(String classificationFile) {

        int numLines = 0;
        Category[] categoryLevels = new Category[MAX_LEVELS + 1];

        System.out.println("Starting processing of YML file with classification hierarchy...");

        //Parse lines from input YML file with classification hierarchy
        try {
            //Consume input file line by line and populate the dictionary
            InputStreamReader reader = new FileReader(classificationFile);
            BufferedReader buf = new BufferedReader(reader);
            String line;
            while ((line = buf.readLine()) != null) {
                numLines++;

                // Ignore empty lines
                if (line.trim().length() == 0)
                    continue;

                // Find indentation
                // Count leading indentation characters (a pair of them signifies another level down in the hierarchy)
                int level = 0;
                for (char c : line.toCharArray()) {
                    if (c != indent)
                        break;
                    level++;
                }

                level = level / 2;   //CAUTION: Two indentation characters signify a level

                // Check if for max depth is exceeded
                if (level > MAX_LEVELS) {
                    System.err.println("ERROR: Line " + numLines + ": Maximum depth of classification is " + MAX_LEVELS + " levels.");
                    System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
                }

                // Get parts
                line = line.trim();
                String[] parts = line.split(splitter, 2);
                if (parts.length != 2) {
                    System.err.println("ERROR: Line " + numLines + ": No '" + splitter + "' character found before identifier.");
                    System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
                }

                String id = parts[1].replace(splitter.charAt(0), ' ').trim();
                String name = parts[0].replace(indent, ' ').trim();

                //Determine its most similar category according to the embedded (default) classification scheme
                Pair<String, Double> embedCategoryAssignment = myEmbeddedClassifier.assignCategory(name);
                String embeddedCategory = embedCategoryAssignment.getLeft();
                double emdeddedScore = embedCategoryAssignment.getRight();

                //CAUTION! On-the-fly generation of a UUID for this category
                String uuid = myAssistant.getUUID(id + name).toString();

//				System.out.println("KEY:" +  id + " CATEGORY: *" + name + "*" + " -> " + embeddedCategory);

                // Add to top tier
                if (level == 0) {
                    //Create a new category
                    Category category = new Category(uuid, id, name, "", embeddedCategory, emdeddedScore);      //No parent category
                    categories.put(name, category);                                              //Use name of category as key when searching
                    categoryUUIDs.put(id, uuid);                                                 //Use original identifier as key for searching

                    // Check for identifier
                    if (id.isEmpty()) {
                        System.err.println("ERROR: Line " + numLines + ": No identifier given for top-tier category.");
                        System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
                    }

                    // Reset category tiers
                    categoryLevels = new Category[MAX_LEVELS + 1];
                    categoryLevels[0] = category;
                    continue;
                }

                // Get parent category
                Category parent = categoryLevels[level - 1];
                if (parent == null) {
                    System.err.println("ERROR: Line " + numLines + ": Invalid indentation.");
                    System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
                }

//				System.out.println(" PARENT KEY:" + parent.getId() + " -> " + parent.getName());

                //If similarity score is lower than the one of its parent, then use the assignment of its parent
                if (parent.getEmbedScore() > emdeddedScore) {
                    embeddedCategory = parent.getEmbedCategory();
                    emdeddedScore = parent.getEmbedScore();
                }

                //Create a new category
                Category category = new Category(uuid, id, name, parent.getId(), embeddedCategory, emdeddedScore);

                // Check if valid
                if (!category.hasId() && !category.hasName()) {
                    System.err.println("ERROR: Line " + numLines + ": Classification must have both a key and a category.");
                    System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
                }

                // An identifier must be specified after a category name at any level
                if (category.hasName() && !category.hasId()) {
                    System.err.println("ERROR: Line " + numLines + ": No identifier provided for a category.");
                    System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
                }

                // Add category to the classification
                categories.put(name, category);                    //Use name of category as key when searching
                categoryUUIDs.put(id, uuid);                       //Use original identifier as key for searching
                categoryLevels[level] = category;

                // Update the number of tiers identified so far
                if (level > numTiers)
                    numTiers = level;
            }
            buf.close();   //Close input file

            //Check if file is empty
            if ((numLines == 0) || (categories.isEmpty())) {
                System.err.println("ERROR: Classification file is empty.");
                System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
            }
        } catch (Exception e) {
            System.err.println("ERROR: Reading classification file failed!");
            System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
        }

        numTiers++;   //top-tier is level 0; so the number of levels must be incremented
        System.out.println("Classification hierarchy reconstructed from YML file.");
    }


    /**
     * Parses input CSV file containing the classification hierarchy
     *
     * @param classificationFile Path to CSV file specifying the classification scheme.
     *                           ASSUMPTION: Each line (record) corresponds to a full path from the top-most to the bottom-most category; at each level, two attributes are given: first a (usually numeric) identifier, then the name of the category.
     *                           ASSUMPTION: This CSV file must use ',' as delimiter character between attributes values enclosed in double quotes ("...").
     *                           EXAMPLE ROW for a 3-tier classification: 1,"Food",103,"Restaurant",103005,"Chinese Restaurant".
     */
    @SuppressWarnings("resource")
    public void parseCSVFile(String classificationFile) {

        int numRecs = 1;                                     //Assuming that the CSV file has a header
        int level;                                           //Level of a category in the classification hierarchy (1 -> top level)

        String id;
        String name;
        Pair<String, Double> embedCategoryAssignment;

        System.out.println("Starting processing of CSV file with classification hierarchy...");

        //Parse lines from input CSV file with classification records
        try {
            //Consume input file line by line
            Reader in = new InputStreamReader(new FileInputStream(classificationFile), "UTF-8");
            CSVFormat format = CSVFormat.RFC4180.withDelimiter(',').withQuote('"').withFirstRecordAsHeader();
            CSVParser dataCSVParser = new CSVParser(in, format);

            //Consume each record and populate the dictionary
            for (CSVRecord rec : dataCSVParser.getRecords()) {
                numRecs++;
                String parent = "";
                String parentCategory = "";
                double parentScore = 0.0;
                String embeddedCategory = "";
                double emdeddedScore = 0.0;
                level = 0;
                for (int i = 0; i < rec.size(); i += 2) {
                    if (rec.get(i + 1).trim().length() > 0) {
                        level++;

                        //Get pairs of attribute values (ID, NAME) from the input record
                        //Also determine its most similar category according to the embedded (default) classification scheme
                        if (!classifyByName) {
                            name = rec.get(i).trim();
                            id = rec.get(i + 1).trim();        //The description of the category is used as join attribute in the data records
                            embedCategoryAssignment = myEmbeddedClassifier.assignCategory(id);
                        } else {
                            id = rec.get(i).trim();         //The identifier of the category is used as join attribute in the data records
                            name = rec.get(i + 1).trim();
                            embedCategoryAssignment = myEmbeddedClassifier.assignCategory(name);
                        }

                        //CAUTION! On-the-fly generation of a UUID for this category
                        String uuid = myAssistant.getUUID(id + name).toString();

                        //Handle embedded categories
                        embeddedCategory = embedCategoryAssignment.getLeft();
                        emdeddedScore = embedCategoryAssignment.getRight();

                        //If similarity score is lower than the one of its parent, then use the assignment of its parent
                        if (parentScore > emdeddedScore) {
                            embeddedCategory = parentCategory;
                            emdeddedScore = parentScore;
                        }

//						System.out.println("KEY:" +  id + " CATEGORY: *" + name + "*" + " -> " + embeddedCategory);

                        //Create a new category
                        Category category = new Category(uuid, id, name, parent, embeddedCategory, emdeddedScore);

                        // Check if valid
                        if (!category.hasId() || !category.hasName()) {
//							System.err.println("ERROR: Line " +  numRecs + ": Classification must have both a key and a category.");
                            continue;
                        }

                        // An identifier must be specified after a category name at any level
                        if (category.hasName() && !category.hasId()) {
                            System.err.println("ERROR: Line " + numRecs + ": No identifier provided for a category at level " + level + ".");
                            System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
                        }

                        //Current category will become the parent of the one at the next (lower) level
                        parent = id;
                        parentCategory = embeddedCategory;
                        parentScore = emdeddedScore;

                        //Append this category if it does not already exist in the dictionary
                        if (categories.containsKey(name))
                            if (categories.get(name).getId().equals(id))
                                continue;

                        //Otherwise, add category to the classification
                        categories.put(name, category);                    //Use name of category as key when searching
                        categoryUUIDs.put(id, uuid);                       //Use original identifier as key for searching

                        // Update the number of tiers identified so far
                        if (level > numTiers)
                            numTiers = level;
                    }
                }
            }

            //Check if file is empty
            if ((numRecs == 0) || (categories.isEmpty())) {
                System.err.println("ERROR: Classification file is empty.");
                System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: Reading classification file failed!");
            System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
        }

        System.out.println("Classification hierarchy reconstructed from CSV file.");
    }


    /**
     * For a given category name, finds the respective entry in the classification scheme
     *
     * @param categoryName The category name to search in the classification hierarchy. CUATION! Category names must be unique amongst all levels and are used as keys.
     * @return The entry in the classification scheme representation corresponding to the given category
     */
    public Category searchByName(String categoryName) {
        if (categories.containsKey(categoryName))
            return categories.get(categoryName);                    //A category may be found at any level in this scheme

        return null;
    }


    /**
     * For a given category identifier, finds the respective entry in the classification scheme
     *
     * @param categoryId The category identifier to search in the classification hierarchy. Category identifiers must be unique amongst all levels.
     * @return The entry in the classification scheme representation corresponding to the given category
     */
    public Category searchById(String categoryId) {
        //Iterate through the dictionary and identify the item with the given id in the classification hierarchy
        for (Category rs : categories.values())
            if (rs.getId().equals(categoryId))
                return rs;

        return null;
    }

    /**
     * For a given category name, identifies its respective UUID in the classification scheme
     *
     * @param categoryName The category name to search in the classification hierarchy. Category names must be unique amongst all levels.
     * @return The UUID corresponding to this category in the classification scheme
     */
    public String getUUID(String categoryName) {
        if (categories.containsKey(categoryName))
            return categories.get(categoryName).getUUID();           //A category may be found at any level in this scheme

        return null;
    }

    /**
     * For a given category identifier in the classification scheme, find its respective UUID
     *
     * @param categoryId The original category identifier to search in the classification hierarchy. Category identifiers must be unique amongst all levels.
     * @return The UUID corresponding to this category identifier in the classification scheme
     */
    public String findUUID(String categoryId) {
        if (categoryUUIDs.containsKey(categoryId))
            return categoryUUIDs.get(categoryId);

        return null;
    }


    /**
     * For a given category name, identifies its respective category in the embedded (default) classification scheme
     *
     * @param categoryName The category name to search in the user-defined classification hierarchy. Category names must be unique amongst all levels.
     * @return The embedded category corresponding to that category in the user-defined classification scheme
     */
    public String getEmbeddedCategory(String categoryName) {
        if (categories.containsKey(categoryName))
            return categories.get(categoryName).getEmbedCategory();           //A category may be found at any level in this scheme

        return null;
    }

    /**
     * Returns the number of categories in the dictionary representation of the classification scheme
     *
     * @return Total number of categories at all levels
     */
    public int countCategories() {
        return categories.size();
    }

    /**
     * Returns the depth, i.e., max number of levels in any path of the given multi-tier classification hierarchy
     *
     * @return Number of levels for a multi-tier classification hierarchy.
     */
    public int countTiers() {
        return numTiers;
    }


    /**
     * Given a parent identifier, recursively find all its descendants and print them in a YML-like fashion
     *
     * @param parent_id Original identifier for the parent of a category
     * @param level     The level of the category in the classification scheme (0: top-tier)
     */
    private void printDescendants(String parent_id, int level) {
        //Iterate through the dictionary and type all descendants of a given item in the classification hierarchy
        for (Category rs : categories.values()) {
            if ((rs.getParent() != null) && (rs.getParent().equals(parent_id))) {
                int lvl = level + 1;
                //Print categories in a YML-like fashion:
                for (int k = 1; k < lvl; k++) {
                    System.out.print(indent);
                    System.out.print(indent);      //Indentation (two blank characters) for each extra level
                }
                System.out.println(rs.getName() + " " + splitter + rs.getId());       //Special character before identifier

                //Recursion at the next level
                if (lvl < MAX_LEVELS)
                    printDescendants(rs.getId(), lvl);
            }
        }
    }


    /**
     * Prints the entire classification scheme representation to the standard output in a YML-like fashion
     */
    public void printHierarchyYML() {
        //Iterate through the dictionary and type all descendants of a given item in the classification hierarchy
        for (Category rs : categories.values()) {
            if (rs.getParent() == null)               //Top-tier categories do not have a parent
            {
                System.out.println(rs.getName() + " " + splitter + rs.getId());       //Special character before identifier
                printDescendants(rs.getId(), 1);
            }
        }
    }


    /**
     * Parses each item in the classification hierarchy and streamlines the resulting triples according to the given YML mapping. Applicable in STREAM transformation mode.
     */
    private void executeParser4Stream() {

        //System.out.println(myAssistant.getGMTime() + " Started processing features...");
        long t_start = System.currentTimeMillis();
        long dt = 0;

        int numRec = 0;
        int numTriples = 0;

        OutputStream outFile = null;
        try {
            outFile = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            LOGGER.error("Output file not specified correctly.");
        }

        //CAUTION! Hard constraint: serialization into N-TRIPLES is only supported by Jena riot (stream) interface
        StreamRDF stream = StreamRDFWriter.getWriterStream(outFile, Lang.NT);
        stream.start();                //Start issuing streaming triples

        TripleGenerator myGenerator = new TripleGenerator(currentConfig, myAssistant);     //Will be used to generate all triples for each category

        try {
            //Iterate through all categories
            for (Category rs : categories.values()) {
                //Clean up any previous triples, in order to collect the new triples derived from the current feature
                myGenerator.clearTriples();

//				System.out.println("CATEGORY: " + rs.printContents());

                //Issue triples according to the attribute used for classification (id or name of categories) in the data
                String val;
                if (!classifyByName)
                    val = rs.getId();
                else
                    val = rs.getName();

                //Pass attribute values to the generator in order to apply custom mapping(s) directly
                //IMPORTANT! This export is currently customized for classification schemes conforming to the SLIPO ontology only
                myGenerator.transformCategory2RDF(rs.getUUID(), val, findUUID(rs.getParent()));

                //Append each triple to the output stream
                for (int i = 0; i <= myGenerator.getTriples().size() - 1; i++) {
                    stream.triple(myGenerator.getTriples().get(i));
                    numTriples++;
                }

                numRec++;
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred during transformation of an input record.");
        }

        //Finally, store results collected in the disk-based RDF graph
        stream.finish();               //Finished issuing triples

        //Measure execution time
        dt = System.currentTimeMillis() - t_start;
        myAssistant.reportStatistics(dt, numRec, 0, numTriples, currentConfig.serialization, myGenerator.getStatistics(), null, currentConfig.mode, null, outputFile, 0);
    }

}