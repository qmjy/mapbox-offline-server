/*
	Copyright 2015, Merten Peetz
	
	This file is part of OsmPoisPbf.
	OsmPoisPbf is free software: you can redistribute it and/or modify it under the terms of the GNU 
	General Public License as published by the Free Software Foundation, either version 3 of the 
	License, or (at your option) any later version.
	
	OsmPoisPbf is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
	even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
	General Public License for more details.
	
	You should have received a copy of the GNU General Public License along with OsmPoisPbj. If not, 
	see http://www.gnu.org/licenses/.
*/

package eu.smartdatalake.athenarc.osmwrangle.osm;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/* DEVELOPMENT HISTORY
 * Created by: Merten Peetz, 2017
 * Modified: 31/5/2018 by Kostas Patroumpas; included method for providing the set of all identified categories
 * Modified: 15/6/2018 by Kostas Patroumpas; included method for providing the set of all identified tags
 * Last modified by: Kostas Patroumpas, 15/6/2018
 */

public class OSMFilterFileParser {
	private static final int MAX_INDENT = 100; // Prevent stack overflow
	private String filename;
	private Set<String> tags;
	private Set<String> categories;
	
	public OSMFilterFileParser(String filename) {
		this.filename = filename;
	}
	
	public List<OSMFilter> parse() {
		
		tags = new HashSet<String>();
		categories = new HashSet<String>();
		
		// Load filter file
		List<String> lines = null;
		try {
			lines = readOSMFilterFile(filename);
		} catch(IOException ex) {
			System.out.println("Error: Reading filter file failed");
			return null;
		}
		
		if(lines == null) {
			System.out.println("Error: Reading filter file failed");
			return null;
		}
		
		// Parse lines
		List<OSMFilter> filters = new ArrayList<OSMFilter>();
		OSMFilter[] filterLevels = new OSMFilter[MAX_INDENT + 1];
		int lineNumber = 0;
		for(String line : lines) {
			lineNumber++;
			
			// Ignore if comment
			if(line.length() > 0 && line.charAt(0) == '#') {
				continue;
			}
			
			// Ignore if empty line
			if(line.trim().length() == 0) {
				continue;
			}
			
			// Get indentation
			// Count leading spaces
			int spaces = 0;
			for(char c : line.toCharArray()) {
				if(c != ' ') {
					break;
				}
				spaces++;
			}
			int level = spaces / 2;
			
			// Check for max indent
			if(level > MAX_INDENT) {
				showError("Max indentation is " + MAX_INDENT + " levels", lineNumber);
				return null;
			}
			
			// Get parts
			line = line.trim();
			String[] parts = line.split("=", 2);
			if(parts.length != 2) {
				showError("No \"=\" character", lineNumber);
				return null;
			}
			String key = parts[0];
			String value = parts[1];
			
			// Get category name
			String category = "";
			parts = value.split(" ", 2);
			if(parts.length == 2) {
				value = parts[0];
				category = parts[1];
			}
			
			// Make filter
			OSMFilter filter = new OSMFilter(key, value, category);
			
			//Include tags to the list
			tags.add(key);
			tags.add(value);
			
			//Include category into the set of identified categories
			if ((category != null) && (!category.trim().isEmpty()))
				categories.add(category);
			
			// Add to highest level
			if(level == 0) {
				filters.add(filter);
				
				// Check for key
				if(key.isEmpty()) {
					showError("No key for top level filter", lineNumber);
					return null;
				}
				
				// Reset filter levels
				filterLevels = new OSMFilter[MAX_INDENT + 1];
				filterLevels[0] = filter;
				continue;
			}
			
			// Get parent
			OSMFilter parent = filterLevels[level - 1];
			if(parent == null) {
				showError("Invalid indentation", lineNumber);
				return null;
			}
			
			// Check if valid
			if(!filter.hasKey() && !filter.hasValue()) {
				showError("OSMFilters must have at least a key or a value", lineNumber);
				return null;
			}
			
			// After a value has to come a key
			if(parent.hasValue() && !filter.hasKey()) {
				showError("No key provided after a value", lineNumber);
				return null;
			}
			
			// Add to parent
			parent.childs.add(filter);
			filterLevels[level] = filter;
		}
		
		// Verify filters
		for(OSMFilter filter : filters) {
			if(!isOSMFilterValid(filter)) {
				System.out.println("Error: Parsing filter file: There is at least one filter with key " + filter.getKey() + " without children that has no category name");
				return null;
			}
		}
		return filters;
	}
	
	// Check that all end points have a category name (recursive)
	private boolean isOSMFilterValid(OSMFilter filter) {
		// Check endpoint (filter without childs)
		if(filter.childs.isEmpty()) {
			// Check that there is a category name
			if(!filter.hasCategory()) {
				return false;
			}
		}
		
		// Iterate childs
		for(OSMFilter child : filter.childs) {
			if(!isOSMFilterValid(child)) {
				return false;
			}
		}
		return true;
	}
	
	// Display an error message with line number
	private void showError(String msg, int lineNumber) {
		System.out.println("Error: Parsing filter file, line " + lineNumber + ": " + msg);
	}
	
	//Return the set of all identified categories
	public Set<String> getAllCategories() {
		return categories;
	}

	//Return the set of all identified tags
	public Set<String> getAllTags() {
		return tags;
	}
	
	private List<String> readOSMFilterFile(String filename) throws IOException {
		// Use default file or customized file
		InputStreamReader reader;
		if(filename == null) {
			InputStream stream = getClass().getResourceAsStream("/filters.txt");
			if(stream == null) {
				return null;
			}
			reader = new InputStreamReader(stream);
		} else {
			reader = new FileReader(filename);
		}
		
		// Read file
		BufferedReader buffered = new BufferedReader(reader);
		List<String> lines = new ArrayList<String>();
		String line;
		while((line = buffered.readLine()) != null) {
			lines.add(line);
		}
		buffered.close();
		return lines;
	}
}