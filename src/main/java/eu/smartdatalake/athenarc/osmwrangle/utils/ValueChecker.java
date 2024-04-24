/*
 * @(#) ValueChecker.java 	 version 2.0   25/10/2018
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlElement;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 27/7/2018
 * Modified: 27/7/2018; replaced any appearance of the delimiter character in string values
 * Modified: 25/10/2018; supporting a resource XML file that lists user-specified search and replacement strings for literals.
 * Last modified: 25/10/2018
 */


/**
 * Class to hold the collection of patterns specified in an XML resource file.
 */
class Patterns {
    @XmlElement(name = "pattern")
    public List<Pattern> patterns;
}

/**
 * An individual pattern with a distinct key, specifying a search string that should be replaced with a replacement string.
 */
class Pattern {
    public String key;
    public String search;
    public String replace;
}

/**
 * Removes or replaces illegal characters from a literal value.
 * LIMITATIONS: Currently handling only some basic cases that may cause trouble (e.g., line breaks) in literals included in RDF triples.
 *
 * @author Kostas Patroumpas
 * @version 2.0
 */
public class ValueChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValueChecker.class);

    private Map<String, Pattern> replacements;     //Dictionary of string values to check (keys) for presence in a given literal and their respective replacements (values)

    /**
     * Constructs a ValueChecker object that will be used for checking (and possibly correcting) literals for specific anomalies before issuing RDF triples
     */
    public ValueChecker() {

        replacements = new HashMap<String, Pattern>();

        try {
            //The resource XML file where correspondence between unwanted characters and their replacements is specified by the user.
            InputStream in = getClass().getResourceAsStream("/wrangle/replacements.xml");            //Open resource XML file as a stream

            //Obtain the entire tree of patterns from the given XML input
            Patterns f = JAXB.unmarshal(in, Patterns.class);
            for (Pattern p : f.patterns) {
                replacements.put(p.key, p);       //Store patterns in the dictionary
            }
        } catch (Exception e) {
            LOGGER.error("Resource XML file with patterns not allowed in literals was not found or malformed!");
        }
    }

    /**
     * Eliminates illegal characters from the given string value (literal)
     *
     * @param val A string value.
     * @return The same string value with any illegal characters removed.
     */
    public String removeIllegalChars(String val) {

        if (val != null) {
            val = findReplacePattern(val, replacements.get("DOUBLE_QUOTE"));   //Replace any double quote with a single quote
            return findReplacePattern(val, replacements.get("TAB_NEWLINE"));   //Replacing newlines with SPACE, but other special characters should be handled as well
        }
        return "";                                             //In case of NULL values, return and empty string
    }

    /**
     * Eliminates any appearance of the delimiter character in the given string value (literal)
     *
     * @param val A string value.
     * @return The same string value after the delimiter character has been replaced.
     */
    public String removeDelimiter(String val) {

        if (val != null)
            return findReplaceSubstring(val, replacements.get("CSV_DEFAULT_DELIMITER"));   //Replace delimiter with a SEMICOLON

        return "";                                                         //In case of NULL values, return an empty string
    }

    /**
     * Restores the given string value as a URL
     *
     * @param val A string value representing a URL
     * @return The same string value as a valid HTTP address
     */
    public String cleanupURL(String val) {

        if (val != null) {
            val = findReplacePattern(val, replacements.get("WHITE_SPACE"));       //Eliminate white spaces and invalid characters
            val = findReplaceSubstring(val, replacements.get("URL_BACKSLASH"));   //Backslash characters '\' are not allowed in URLs
            val = findReplacePattern(val, replacements.get("VALIDATE_URL"));      //Any invalid characters like <, >, |, " are eliminated from this URL

            if (!val.toLowerCase().matches("^\\w+://.*"))     //This value should be a URL, so put HTTP as its prefix
                val = "http://" + val;                        //In case that no protocol has been specified, assume that this is HTTP
        }
        return val;
    }

    /**
     * Remove whitespace characters from a string literal
     *
     * @param val A string value that possibly contains whitespace
     * @return The original string with any whitespace characters replaced
     */
    public String replaceWhiteSpace(String val) {

        if (val != null)
            return findReplaceSubstring(val, replacements.get("WHITE_SPACE"));

        return val;
    }

    /**
     * Find a given substring and replace it with another string in a literal.
     *
     * @param val A literal value to be searched for the substring.
     * @param p   A pattern specifying a search and a replacement string (specified in the external resource XML file).
     * @return The modified literal after replacement.
     */
    public String findReplaceSubstring(String val, Pattern p) {

        return val.replace(p.search, p.replace);
    }

    /**
     * Find a given pattern (i.e., regular expression) and replace it with another string in a literal.
     *
     * @param val A literal value to be searched for the pattern.
     * @param p   A pattern specifying a search regular expression and a replacement string (specified in the external resource XML file).
     * @return The modified literal after replacement.
     */
    public String findReplacePattern(String val, Pattern p) {

        return val.replaceAll(p.search, p.replace);
    }
}
