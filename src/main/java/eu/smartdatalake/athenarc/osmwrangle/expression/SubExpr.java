/*
 * @(#) SubExpr.java	version 2.0   10/7/2019
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
import java.util.Map;

/**
 * Handles sub-expressions (possibly enclosed in parentheses) in a logical expression.
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 4/7/2019
 * Last modified: 10/7/2019
 */
public class SubExpr implements Expr {
	
    private Expr child;

    /**
     * Constructor of the class.
     * @param stream   Input stream of tokens.
     * @throws ParseException
     */
    public SubExpr(TokenStream stream) throws ParseException {
    	
    	//Recognize a sub-expression 
        if(stream.consumeIf(TokenType.LEFT_PAREN) != null)  //This is enclosed in parentheses
        {
            child = new OrExpr(stream);
            stream.consume(TokenType.RIGHT_PAREN);
        } 
        else                                                //... or it is a simple expression with one comparison operator
        	child = new SimpleExpr(stream);
    }


    @Override
    public String toString() {
        return "(" + child + ")";
    }


    @Override
    public boolean evaluate(Map<String, String> data) {
        return child.evaluate(data);
    }
}