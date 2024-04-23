/*
 * @(#) TripleGenerator.java  version 2.0  5/12/2019
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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Retains mappings of feature attributes (input) to RDF predicates (output) that will be used in generation of triples.
 *
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 21/12/2017
 * Modified: 21/12/2017, added reverse dictionary for each RDF predicate
 * Modified: 23/12/2017, added support for reading mappings from YML file
 * Modified: 25/4/2018; included specification for multi-faceted (mainly multi-lingual) attributes with wild char '%LANG'
 * Modified: 30/4/2018; included specification for geometry-based, built-in functions
 * Modified: 11/5/2018; included specification for literals with language tags; built-in functions with arguments
 * Modified: 9/10/2018; included specification for custom URIs
 * Modified: 10/5/2019; extended support for multi-faceted properties with wild char '*'
 * Modified: 4/7/2019; allowing literal values (quoted strings) to be given as arguments in thematic built-in functions
 * Modified: 7/10/2019; allowing literal values (quoted strings) as arguments in geometric built-in functions
 * Last modified: 5/12/2019
 */

public class Mapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(Mapping.class);

    /**
     * Helper class containing enumeration of possible mapping profiles to be applied in triple generation.
     *
     * @author Kostas Patroumpas
     */
    public enum MappingProfile {
        IS_INSTANCE,                 //Properties that are instances of classes
        IS_INSTANCE_TAG_LANGUAGE,    //Properties that are instances of classes, but literals should have language tags as well
        IS_PART,                     //Properties that are part of composite classes
        IS_PART_TAG_LANGUAGE,        //Properties that are part of composite classes, but literals should have language tags as well
        HAS_DATA_TYPE,               //Properties with literals having data type specifications
        HAS_DATA_TYPE_URL,           //Properties with objects that are URLs
        IS_LITERAL_TAG_LANGUAGE,     //Property with a plain literal with language tag
        IS_LITERAL,                  //Property with a plain literal without further specifications
        IS_URI,                      //Custom handling of URIs
        UNSPECIFIED;                 //No specification
    }

    /**
     * Helper class containing properties of a particular mapping.
     *
     * @author Kostas Patroumpas
     */
    public class mapProperties {

        String entityType;
        String predicate;
        String resourceType;
        String language;
        String instance;
        String part;
        String generatorFunction;
        List<String> functionArguments;
        RDFDatatype dataType;
        MappingProfile profile;

        /**
         * Constructor of mapProperties class.
         */
        public mapProperties() {
            entityType = predicate = resourceType = language = instance = part = generatorFunction = null;
            functionArguments = new ArrayList<String>();
            dataType = null;
            profile = null;
        }

        //Setter methods

        /**
         * Sets or updates the entity type of the resource.
         *
         * @param e The user-specified entity type.
         */
        public void setEntityType(String e) {
            entityType = e;
        }

        /**
         * Sets or updates the predicate property of the resource.
         *
         * @param p The user-specified predicate.
         */
        public void setPredicate(String p) {
            predicate = p;
        }

        /**
         * Sets or updates the type of the resource.
         *
         * @param r The user-specified type of the resource.
         */
        public void setResourceType(String r) {
            resourceType = r;
        }

        /**
         * Sets or updates the language tag to be used in string literals for this resource.
         *
         * @param l The language tag for string literals.
         */
        public void setLanguage(String l) {
            language = l;
        }

        /**
         * Sets or updates the ontology class that this resource is an instance of.
         *
         * @param i The name of the class in the ontology.
         */
        public void setInstance(String i) {
            instance = i;
        }

        /**
         * Sets or updates the name of parent property that this resource is part of. E.g., street name is part of the address property.
         *
         * @param p The name of the parent property in the ontology.
         */
        public void setPart(String p) {
            part = p;
        }

        /**
         * Sets or updates the name of built-in function that will generate the values for this attribute.
         *
         * @param f The name of the built-in function (this function is declared in the Assistant class).
         */
        public void setGeneratorFunction(String f) {
            generatorFunction = f;
        }

        /**
         * Sets or updates an argument for a built-in function that will generate the values for this attribute.
         *
         * @param a The name of the argument to be used for the built-in function (this function declared in the Assistant class).
         */
        public void setFunctionArgument(String a) {
            functionArguments.add(a.trim());
        }

        /**
         * Sets or updates the data type for literals of a resource.
         * IMPORTANT! Currently handles only data types utilized in the SLIPO ontology.
         *
         * @param d The XSD data type to be assigned to literals.
         */
        public void setDataType(String d) {

            switch (d.toLowerCase()) {
                case "int":
                case "integer":
                    dataType = XSDDatatype.XSDinteger;
                    break;
                case "long":
                    dataType = XSDDatatype.XSDlong;
                    break;
                case "float":
                    dataType = XSDDatatype.XSDfloat;
                    break;
                case "double":
                    dataType = XSDDatatype.XSDdouble;
                    break;
                case "date":
                    dataType = XSDDatatype.XSDdate;
                    break;
                case "datetime":
                    dataType = XSDDatatype.XSDdateTime;
                    break;
                case "timestamp":
                    dataType = XSDDatatype.XSDdateTimeStamp;
                    break;
                case "boolean":
                    dataType = XSDDatatype.XSDboolean;
                    break;
                case "uri":
                    dataType = XSDDatatype.XSDanyURI;
                    break;
                default:
                    dataType = XSDDatatype.XSDstring;
                    break;                      //Every other data type is handled as a string
            }
        }

        /**
         * Sets or updates the profile to be applied during generation of triples for this attribute according to the user-specified mappings.
         * This method should be called once all other mapping properties have been determined for this attribute.
         */
        public void setMappingProfile() {

            if ((resourceType != null) && (instance != null) && (part == null)) {   //Handling of properties that are instances of classes, e.g., CONTACT (phone, fax, email), NAME, ACCURACY in the SLIPO ontology
                if (language != null)
                    profile = MappingProfile.IS_INSTANCE_TAG_LANGUAGE;
                else
                    profile = MappingProfile.IS_INSTANCE;
            } else if (part != null) {    //Specific handling of composite properties (e.g., like ADDRESS consisting of street name, house number, postal code, etc.)
                if (language != null)
                    profile = MappingProfile.IS_PART_TAG_LANGUAGE;
                else
                    profile = MappingProfile.IS_PART;
            } else if (dataType != null) {   //Specific handling of literals with data type specifications
                if (dataType.getClass().isInstance(XSDDatatype.XSDanyURI))
                    profile = MappingProfile.HAS_DATA_TYPE_URL;                //Object is of type URL
                else
                    profile = MappingProfile.HAS_DATA_TYPE;
            } else if ((predicate != null) && (language != null))
                profile = MappingProfile.IS_LITERAL_TAG_LANGUAGE;
            else if (predicate != null)
                profile = MappingProfile.IS_LITERAL;
            else if (entityType.equalsIgnoreCase("URI"))                       //Specific handling for URIs (not actually generating any property)
                profile = MappingProfile.IS_URI;
            else
                profile = MappingProfile.UNSPECIFIED;

        }


        //Getter methods

        /**
         * Provides the entity type of the resource.
         *
         * @return The entity type of a resource.
         */
        public String getEntityType() {
            return entityType;
        }

        /**
         * Provides the predicate property of the resource.
         *
         * @return The predicate property.
         */
        public String getPredicate() {
            return predicate;
        }

        /**
         * Provides the type of the resource.
         *
         * @return The type of the resource.
         */
        public String getResourceType() {

            if (resourceType == null)
                return predicate.substring(predicate.indexOf(':') + 1);        //Resource type inferred from the predicate

            return resourceType;
        }

        /**
         * Provides the built-in function and its arguments that will generate the type of the resource.
         *
         * @return A string array with the function name and its arguments; function name is the LAST item in the array
         */
        public String[] getResourceTypeFunction() {
            if ((resourceType != null) && resourceType.startsWith("generateWith.")) {
                String[] args = resourceType.substring(resourceType.indexOf('(') + 1, resourceType.length() - 1).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                String[] res = ArrayUtils.addAll(args, resourceType.substring(resourceType.indexOf('.') + 1, resourceType.indexOf('(')));
                return res;
            }

            return null;
        }

        /**
         * Provides the language tag used in string literals for this resource.
         *
         * @return The language tag.
         */
        public String getLanguage() {
            return language;
        }

        /**
         * Provides the ontology class that this resource is an instance of.
         *
         * @return The name of the class in the ontology.
         */
        public String getInstance() {
            return instance;
        }

        /**
         * Provides the name of parent property that this resource is part of.
         *
         * @return The name of the parent property in the ontology.
         */
        public String getPart() {
            return part;
        }

        /**
         * Provides the name of built-in function that will be used to generate values for this attribute.
         *
         * @return The name of the built-in function.
         */
        public String getGeneratorFunction() {
            return generatorFunction;
        }

        /**
         * Provides the list of arguments where a built-in function will be applied in order to generate values for this attribute.
         *
         * @return The list of attributes (usually, attribute names in the original dataset).
         */
        public List<String> getFunctionArguments() {
            return functionArguments;
        }

        /**
         * Provides the data type used for literals of a resource.
         *
         * @return The XSD data type being assigned to literals.
         */
        public RDFDatatype getDataType() {
            return dataType;
        }

        /**
         * Provides the profile to be applied in the generation of triples regarding this attribute according to the mapping.
         *
         * @return A particular profile that controls how to create triples for this attribute.
         */
        public MappingProfile getMappingProfile() {
            return this.profile;
        }
    }

    /**
     * Basic structure with mappings of feature attributes (key) to RDF predicates (values)
     */
    Map<String, mapProperties> attrMappings;

    /**
     * Internal structure that keeps the names of all multi-faceted attributes (e.g., names in various languages)
     */
    List<String> multiFacetedAttrs;

    /**
     * List that retains the names of the thematic attributes to be auto-generated. These attributes MUST not appear in the original dataset to be transformed.
     */
    List<String> extraThematicAttrs;

    /**
     * List that retains the names of the geometric attributes to be auto-generated. These attributes MUST not appear in the original dataset to be transformed.
     */
    List<String> extraGeometricAttrs;

    /**
     * Constructor of the Mapping class.
     */
    public Mapping() {

        this.attrMappings = new HashMap<String, mapProperties>();
        this.multiFacetedAttrs = new ArrayList<String>();
        this.extraThematicAttrs = new ArrayList<String>();
        this.extraGeometricAttrs = new ArrayList<String>();
    }

    /**
     * Create a new mapping for a specific attribute in the input dataset.
     *
     * @param key      The name of the attribute in the input dataset.
     * @param mappings The mapping properties as specified in a YML file for this attribute.
     */
    public void add(String key, String[] mappings) {

        mapProperties props = new mapProperties();

        //Store each property of the user-specified mapping
        for (int i = 0; i < mappings.length; i++) {
            switch (i) {
                case 0:
                    props.setEntityType(mappings[i]);
                    break;
                case 1:
                    props.setPredicate(mappings[i]);
                    break;
                case 2:
                    props.setResourceType(mappings[i]);
                    break;
                case 3:
                    props.setLanguage(mappings[i]);
                    break;
                case 4:
                    props.setInstance(mappings[i]);
                    break;
                case 5:
                    props.setPart(mappings[i]);
                    break;
                case 6:
                    if (mappings[i].startsWith("geometry")) {
                        String func = mappings[i].substring(9);                 //Strip off the "geometry." prefix from the function name
                        //Special handling in order to identify possible arguments to be used by the built-in function
                        int p = func.indexOf('(');
                        if (p >= 0) {
                            props.setGeneratorFunction(func.substring(0, p));    //Keep only the name of the function
                            String[] args = func.substring(p + 1, func.length() - 1).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                            for (String arg : args)
                                props.setFunctionArgument(arg.trim());    //Specify the arguments
                        } else
                            props.setGeneratorFunction(func);                //No arguments given by user
                        this.extraGeometricAttrs.add(key);
                    } else {   //Special handling in order to identify possible arguments to be used by the built-in function
                        int p = mappings[i].indexOf('(');
                        if (p >= 0) {
                            props.setGeneratorFunction(mappings[i].substring(0, p));    //Keep only the name of the function
                            String[] args = mappings[i].substring(p + 1, mappings[i].length() - 1).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                            for (String arg : args)
                                props.setFunctionArgument(arg.trim());                    //Specify the arguments
                        } else
                            props.setGeneratorFunction(mappings[i]);                      //Only the function name has been specified

                        //Add this property to extra thematic attributes
                        this.extraThematicAttrs.add(key);
                    }
                    break;
                case 7:
                    props.setDataType(mappings[i]);
                    break;
            }
        }

        //Once all properties have been specified, determine the particular profile to be applied during generation of triples for this attribute
        props.setMappingProfile();

        //In case of a URI specification, this should not be considered along with thematic attributes
        if (props.getMappingProfile().equals(MappingProfile.IS_URI))
            this.extraThematicAttrs.remove(key);

        //Check whether the key (i.e., attribute name) contains wildcards '%' or '*' used to distinguish multi-faceted attributes
        if (key.endsWith("%LANG"))                            //Specification for multi-lingual properties
        {
            key = key.substring(0, key.indexOf('%'));         //Keep this attribute name after stripping off the suffix
            this.multiFacetedAttrs.add(key);
        } else if (key.contains("*"))                           //Other multi-item properties
        {
            this.multiFacetedAttrs.add(key);                  //Keep this attribute name as is, including the wildcard character
        }

        //Hold these properties using the attribute name as key
        this.attrMappings.put(key, props);
    }

    /**
     * Provides all attribute names listed in the user-specified mapping.
     *
     * @return A set of attribute names.
     */
    public Set<String> getKeys() {

        return this.attrMappings.keySet();
    }

    //

    /**
     * Creates a new mapping specification from a YML file. This file specifies mappings from attribute names of the input to RDF properties in the output triples.
     *
     * @param fileName Path to the YML file.
     */
    public void createFromFile(String fileName) {

        try {
            //Parse a valid YML file
            Yaml yaml = new Yaml();
            FileInputStream f = new FileInputStream(new File(fileName));

            try {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, String>> map = (Map<String, Map<String, String>>) yaml.load(f);
                for (String key : map.keySet()) {
                    mapProperties props = new mapProperties();

                    for (String subkey : map.get(key).keySet()) {
                        switch (subkey) {
                            case "entity":
                                props.setEntityType(map.get(key).get(subkey));
                                break;
                            case "predicate":
                                props.setPredicate(map.get(key).get(subkey));
                                break;
                            case "type":
                                props.setResourceType(map.get(key).get(subkey));
                                break;
                            case "language":
                                props.setLanguage(map.get(key).get(subkey));
                                break;
                            case "instanceOf":
                                props.setInstance(map.get(key).get(subkey));
                                break;
                            case "partOf":
                                props.setPart(map.get(key).get(subkey));
                                break;
                            case "generateWith":
                                if (map.get(key).get(subkey).startsWith("geometry"))                     //Prefix for any geometry-based built-in function to be applied
                                {
                                    String func = map.get(key).get(subkey).substring(9);                 //Strip off the "geometry." prefix from the function name
                                    //Special handling in order to identify possible arguments to be used by the built-in function
                                    int p = func.indexOf('(');
                                    if (p >= 0) {
                                        props.setGeneratorFunction(func.substring(0, p));    //Keep only the name of the function
                                        String[] args = func.substring(p + 1, func.length() - 1).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                                        for (String arg : args)
                                            props.setFunctionArgument(arg);        //Specify the arguments
                                    } else
                                        props.setGeneratorFunction(func);        //No arguments given by user
                                    this.extraGeometricAttrs.add(key);
                                } else {   //Special handling in order to identify possible arguments to be used by the built-in function
                                    int p = map.get(key).get(subkey).indexOf('(');
                                    if (p >= 0) {
                                        props.setGeneratorFunction(map.get(key).get(subkey).substring(0, p));    //Keep only the name of the function
                                        String[] args = map.get(key).get(subkey).substring(p + 1, map.get(key).get(subkey).length() - 1).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                                        for (String arg : args)
                                            props.setFunctionArgument(arg);                           //Specify the arguments
                                    } else
                                        props.setGeneratorFunction(map.get(key).get(subkey));         //Only the function name has been specified

                                    //Add this property to extra thematic attributes
                                    this.extraThematicAttrs.add(key);
                                }
                                break;
                            case "datatype":
                                props.setDataType(map.get(key).get(subkey));
                                break;
                        }
                    }

                    //Once all properties have been specified, determine the particular profile to be applied during generation of triples for this attribute
                    props.setMappingProfile();

                    //In case of a URI specification, this should not be considered along with thematic attributes
                    if (props.getMappingProfile().equals(MappingProfile.IS_URI))
                        this.extraThematicAttrs.remove(key);

                    //Check whether the key (i.e., attribute name) contains wildcards '%' or '*' used to distinguish multi-faceted attributes
                    if (key.endsWith("%LANG"))                            //Specification for multi-lingual properties
                    {
                        key = key.substring(0, key.indexOf('%'));         //Keep this attribute name after stripping off the suffix
                        this.multiFacetedAttrs.add(key);
                    } else if (key.contains("*"))                           //Other multi-item properties
                    {
                        this.multiFacetedAttrs.add(key);                  //Keep this attribute name as is, including the wildcard character
                    }

                    //Hold these properties using the attribute name as key
                    this.attrMappings.put(key, props);

                }
            } catch (Exception e) {
                LOGGER.error("ERROR: Cannot load YML file to a particular mapping.");
            }
        } catch (Exception e) {
            LOGGER.error("ERROR: Cannot parse the specified YML file with attribute mappings.");
        }
    }


    /**
     * Removes the mapping specification for a given attribute (used as key in the mapping).
     *
     * @param key The name of the attribute to be removed.
     * @return True if attribute has been removed; otherwise, False.
     */
    public boolean remove(String key) {

        if (this.attrMappings.containsKey(key)) {
            this.attrMappings.remove(key);           //Key exists
            return true;
        }

        return false;                                //Key not found
    }

    /**
     * Retrieve the properties associated with a given attribute.
     *
     * @param key the name of the attribute.
     * @return The mapping specifications associated with this attribute.
     */
    public mapProperties find(String key) {

        if (this.attrMappings.containsKey(key))
            return this.attrMappings.get(key);           //Key exists

        return null;
    }

    /**
     * Identifies whether a multi-faceted attribute is included in the mapping specifications.
     *
     * @param key The name of the attribute as used in the dataset.
     * @return The name of its corresponding multi-faceted attribute in the mapping, i.e., without the suffix following the wild char '%' in the specification.
     */
    public String findMultiFaceted(String key) {

        for (String item : this.multiFacetedAttrs) {
            //Check whether multi-faceted attribute exists
            if (key.startsWith(item))        //Wildcard with language specs at the end of attribute name in the mapping
                return item;
            else if (item.contains("*"))     //Wildcard inside the attribute name in the mapping; used for multi-item properties
            {
                String[] parts = item.split("\\*");
                if ((key.startsWith(parts[0]) && (key.endsWith(parts[parts.length - 1]))))
                    return item;
            }
        }

        return null;
    }

    /**
     * Identifies the name(s) of any auto-generated attribute(s) based on geometric properties (e.g., area, length, perimeter).
     *
     * @param f The name of the built-in function that will be used to generate values for any such attribute.
     * @return A list with the names of the auto-generated attribute(s).
     */
    public List<String> findExtraGeometricAttr(String f) {

        List<String> items = new ArrayList<String>();
        for (String item : this.extraGeometricAttrs)
            if (this.find(item).getGeneratorFunction().equals(f))              //A geometry-based function has been defined for this attribute
                items.add(item);

        return items;                                                          //Multiple attributes may have been declared with the same generator function
    }


    /**
     * Provides a list with the names of all thematic attributes that will be generated on-the-fly during transformation.
     *
     * @return List of thematic attributes to be dynamically added to those originally defined in the dataset.
     */
    public List<String> getExtraThematicAttributes() {

        return this.extraThematicAttrs;
    }

    /**
     * Provides a list with the names of all geometric attributes that will be generated on-the-fly during transformation.
     *
     * @return List of geometric attributes to be dynamically added to those originally defined in the dataset.
     */
    public List<String> getExtraGeometricAttributes() {

        return this.extraGeometricAttrs;
    }

    /**
     * Count the specified mappings stored in an instance of this class.
     *
     * @return An integer value representing the number of specified mappings.
     */
    public int countMappings() {

        return this.attrMappings.size();
    }
}
