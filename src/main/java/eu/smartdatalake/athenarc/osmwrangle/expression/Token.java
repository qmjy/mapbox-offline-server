/*
 * @(#) Token.java	version 2.0   10/7/2019
 *
 * Copyright (C) 2013-2019 Information Management Systems  Institute, Athena R.C., Greece.
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
package eu.smartdatalake.athenarc.osmwrangle.expression;

/**
 * Representation of a token recognizable in a logical expression.
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 4/7/2019
 * Last modified: 10/7/2019
 */
public class Token {
    final TokenType type;
    final int start; 
    final String data;

    /**
     * Constructor of the class.
     * @param type  Type of the token.
     * @param start	 Start position in input (for error reporting).
     * @param data  The actual token, e.g., a specific identifier, comparison operator or literal included in the logical expression.
     */
    public Token(TokenType type, int start, String data) {
        this.type = type;
        this.start = start;
        this.data = data;        
    }

    @Override
    public String toString() {
        return type + "[" + data + "]";
    }
}
