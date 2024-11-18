/*
 * @(#) EmbeddedClassifier.java	 version 2.0  25/10/2019
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns a category based on the textual similarity of a given description to a collection of tags characterizing the category.
 * Correspondence between default (embedded) categories and tags is stored in a resource YML file (dictionary).
 * @author Kostas Patroumpas
 * @version 2.0 
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 10/12/2018
 * Modified: 25/10/2019; Also return the similarity score of the assigned category
 * Last modified by: Kostas Patroumpas, 25/10/2019
 */
public class EmbeddedClassifier {
	
	private Map<String, List<String>> categories;            //Dictionary with the correspondence of tags to embedded categories
	                                                         //A tag refers to a single category only; A category may contain multiple tags

	/**
	 * Constructor of the embedded classification used in assigning default categories
	 */
	public EmbeddedClassifier() {
	
		//Parse the resource file containing the default classification scheme (in YML format) and use it as the internal representation of categories
		try {
			buildDefaultClassificationYML();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.err.println("ERROR: Reading default classification file failed!");
			System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
		}		
	}


	/**
	 * Parses a resource YML file and populates the dictionary of tags and their correspondence to embedded categories.
	 * @throws FileNotFoundException
	 */
	private void buildDefaultClassificationYML() throws FileNotFoundException {
	
		char indent = ' ';     //Character used for indentation in YML file: a PAIR of such same characters is used to mark levels in the default classification hierarchy
		int numLines = 0;
		String cat = null;
		
		//Parse lines from input YML resource file with default classification hierarchy	
		try {
			//Initialize dictionary that will hold all tags per category (key)
			categories = new HashMap<String, List<String>>();
			
			//Consume input resource file line by line and populate the dictionary
  			String resourceFile = "/categories.yml";                        //CAUTION! Default classification is stored in YML format
			BufferedReader buf = new BufferedReader(new InputStreamReader(EmbeddedClassifier.class.getResourceAsStream(resourceFile)));
			String line;
			while ((line = buf.readLine()) != null) 
			{
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
				
				level = level/2;   //CAUTION: Two indentation characters signify an extra level in the hierarchy
				
				//Get string
				line = line.trim();
				
				if (level == 0) {
					cat = line;
					categories.put(cat, new ArrayList<String>());          //Create a new key in the dictionary
				}
				else
					categories.get(cat).add(line);                         //Add an extra tag under this key
			}
			buf.close();   //Close input file
			
//			System.out.println("Entries:" + categories.size());
			
			//Check whether file is empty or no tags have been collected
			if ((numLines == 0) || (categories.isEmpty())) {
				System.err.println("ERROR: Default classification file is empty.");
				System.exit(1);                          //Issue signal to the operation system that execution terminated abnormally
			}
		}
		catch(Exception e) {
			System.err.println("ERROR: Reading default classification file failed!");
			System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
		}
  		
	}


	/**
	 * NOT USED: Parses a resource PROPERTIES file and populates the dictionary of tags and their correspondence to categories.
	 * @throws FileNotFoundException
	 */
/*	
	private void buildDefaultClassificationProps() throws FileNotFoundException {
		
  		Properties prop = new Properties();
  		
  		InputStream input = null;
  		try {
  			
  			String resourceFile = "categories.properties";                //CAUTION! Default classification is stored in a PROPERTIES file
    		input = Classifier.class.getClassLoader().getResourceAsStream(resourceFile);
    		if (input == null) {
    	            System.out.println("Unable to find resource file " + resourceFile + " with default classification scheme.");
    		    return;
    		}

  			prop.load(input);
  			
  			//Populate dictionary with all tags per category (key)
  			categories = new HashMap<String, List<String>>(); 			
  			for (String key : prop.stringPropertyNames()) 
  			{   //CAUTION! Assuming that tags are listed in a comma delimited string
  				List<String> tags = Arrays.asList(Arrays.stream(prop.getProperty(key).split(",")).map(String::trim).toArray(String[]::new));
  				categories.put(key, tags);  			    
  			 
  			}
  		} catch (IOException e) {
  			e.printStackTrace();
  			System.err.println("ERROR: Reading default classification file failed!");
			System.exit(1);                              //Issue signal to the operation system that execution terminated abnormally
  		}
  		
	}
*/
	
	/**
	 * Given a textual description, search the dictionary for the most similar tag and assign the corresponding category.
	 * @param val   The textual description of a category (may be a single word, or a multi-word characterization)
	 * @return  The category that has obtained the highest textual similarity score with the given description; this score is also returned.
	 */
	public Pair<String, Double> assignCategory(String val) {
		
			String assignedCategory = null;
			double assignedScore = 0.0;
			
			//CAUTION! All categories and tags in the dictionary are in upper case letters...
			val = val.toUpperCase();   //... so turn this value in upper case too
			
			//Iterate over all categories
			for (String cat: categories.keySet()) 
			{
				//Get all tags corresponding to that category
				List<String> tags = categories.get(cat);
				tags.add(cat);    //Include also the name of the category as an extra tag
				
	  			for (String tag: tags) 
	  			{
	               /*
		  			  //NOT USED: Compute Levenshtein similarity score:
		  			  int distLevenshtein = StringUtils.getLevenshteinDistance(tag, val);
		  			  double simLevenshtein = 1.0 - ((double) distLevenshtein) / (Math.max(tag.length(), val.length()));	  			
	               */
		  			  
	  				//Compute Jaro-Winkler similarity score:
	  				double simJaroWinkler = StringUtils.getJaroWinklerDistance(tag, val);

	  				//Keep the category that has obtained the highest score so far
		  			if (simJaroWinkler > assignedScore) 
		  			{
		  				 assignedScore = simJaroWinkler;
		  				 assignedCategory = cat;
		  			}	  				  
	  			}
			}		
//			System.out.println("CATEGORY-> " +  assignedCategory + " score= " + assignedScore);
			
			return Pair.of(assignedCategory, assignedScore);
	}		
}
