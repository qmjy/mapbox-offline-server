/*
 * @(#) TokenStream.java	version 2.0   10/7/2019
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

import java.text.ParseException;
import java.util.List;

/**
 * Recognizes the type of the input tokens recognized in a logical expression.
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 4/7/2019
 * Last modified: 10/7/2019
 */
public class TokenStream {
	
    final List<Token> tokens;
    int offset = 0;

    /**
     * Constructor or the class.
     * @param tokens   The list of recognized tokens.
     */
    public TokenStream(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Consumes next token of the given type.
     * @param type  The type of the token to consume.
     * @return   The recognized token.
     * @throws ParseException  Throws exception if type differs.
     */
    public Token consume(TokenType type) throws ParseException {
        Token token = tokens.get(offset++);
        if (token.type != type) {
            throw new ParseException("Unexpected token at " + token.start
                    + ": " + token + " (was looking for " + type + ")",
                    token.start);
        }
        return token;
    }

    /**
     * Consumes token of given type; it pauses and does not consume the rest if type differs.
     * @param type  The type of the token to consume.
     * @return The recognized token.
     */
    public Token consumeIf(TokenType type) {
        Token token = tokens.get(offset);
        if (token.type == type) {
            offset++;
            return token;
        }
        return null;
    }

    @Override
    public String toString() {
        return tokens.toString();
    }
}