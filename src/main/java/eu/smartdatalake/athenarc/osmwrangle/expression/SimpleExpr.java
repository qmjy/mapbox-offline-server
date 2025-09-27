/*
 * @(#) SimpleExpr.java	version 2.0   11/7/2019
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

import org.apache.commons.lang3.math.NumberUtils;

import java.text.ParseException;
import java.util.Map;

/**
 * Handles a simple logical expression consisting of an identifier, a comparison operator and a literal (string or numeric).
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 4/7/2019
 * Last modified: 11/7/2019
 */
public class SimpleExpr implements Expr {
	
    private final String identifier, literal;
    private TokenType comp;

    /**
     * Constructor of the class.
     * @param stream  Input stream of tokens
     */
    public SimpleExpr(TokenStream stream) throws ParseException {
    	
        Token token = stream.consumeIf(TokenType.IDENTIFIER);

        Token op;
        comp = null;                  //The identified comparison operator. Must be one among: =, <>, LIKE, <, <=, >, >=.
        if (token != null) 
        {
            this.identifier = token.data;                   //Identifier (i.e., attribute name)
            
            //Determine the type of the comparison operator
            op = stream.consumeIf(TokenType.LIKE);
            if (op != null)
            	comp = TokenType.LIKE;                  	//Comparison operator is LIKE

            op = stream.consumeIf(TokenType.EQUALS);
            if (op != null)
            	comp = TokenType.EQUALS;                  	//Comparison operator is =
            else
            {
            	op = stream.consumeIf(TokenType.NOT_EQUALS);
            	if (op != null)
            		comp = TokenType.NOT_EQUALS;			//Comparison operator is <>
            	else
            		op = stream.consumeIf(TokenType.LESS_THAN);
            }
            if ((comp == null) && (op != null))				//Checking for <= or <
            {
            	op = stream.consumeIf(TokenType.EQUALS);
            	if (op != null)
            		comp = TokenType.LESS_THAN_OR_EQUAL;	//Comparison operator is <=
            	else
            		comp = TokenType.LESS_THAN;				//Comparison operator is <
            }
            if ((comp == null) && (op == null))				//Checking for >= or >
            {
            	op = stream.consumeIf(TokenType.GREATER_THAN);
            	if (op != null)
            	{
	            	op = stream.consumeIf(TokenType.EQUALS);
	            	if (op != null)
	            		comp = TokenType.GREATER_THAN_OR_EQUAL;	//Comparison operator is >=
	            	else
	            		comp = TokenType.GREATER_THAN;			//Comparison operator is >
            	}           	
            }     
        
            this.literal = stream.consume(TokenType.LITERAL).data;    //Literal, i.e., string or numeric value
        } 
        else 
        {	//Handles the case when literal is placed before the identifier; LIMITATION: In this case, only = is allowed as comparison operator.
            this.literal = stream.consume(TokenType.LITERAL).data;
            this.identifier = stream.consume(TokenType.IDENTIFIER).data;
        }
    }

    @Override
    public String toString() {
        return identifier + comp.toString() + "'" + literal + "'";
    }

    @Override
    public boolean evaluate(Map<String, String> data) {
    	
    	if (comp.equals(TokenType.EQUALS))               	//Equality of string values
    		return literal.equals(data.get(identifier));
    	else if (comp.equals(TokenType.NOT_EQUALS))         //Inequality of string values
    		return !literal.equals(data.get(identifier));
    	else if (comp.equals(TokenType.LIKE))               //Partial matching of string values using LIKE operator
    		return like(data.get(identifier), literal);
    	else                                                //Evaluate any other comparison operator involving inequality
    	{
    		if (NumberUtils.isCreatable(literal))              //Check inequality for numeric values only
    		{
    			double val = Double.parseDouble(literal);
    			if (comp.equals(TokenType.LESS_THAN))    				// operator is <
    				return (Double.parseDouble(data.get(identifier)) < val);
    			else if (comp.equals(TokenType.LESS_THAN_OR_EQUAL))    	// operator is <=
    				return (Double.parseDouble(data.get(identifier)) <= val);
    			else if (comp.equals(TokenType.GREATER_THAN))   		// operator is >
    				return (Double.parseDouble(data.get(identifier)) > val);
    			else if (comp.equals(TokenType.GREATER_THAN_OR_EQUAL))	// operator is >=
    				return (Double.parseDouble(data.get(identifier)) >= val);
    		}
    	}
    	return false;
    }

    /**
     * Evaluates an expression involving the LIKE operator.
     * @param val  The input value to check.
     * @param expr  The expression including wild characters like %, ?, etc. as in SQL.
     * @return True if the input value matches with the expression; otherwise, False.
     */
    private boolean like(String val, String expr) {
    	
    	//Turn the SQL-like expression into a Java-compatible regular expression.
        expr = expr.toLowerCase(); 			// ignoring locale for now
        expr = expr.replace(".", "\\."); 	// "\\" is escaped to "\"
        // ... escape any other potentially problematic characters here
        expr = expr.replace("?", ".");
        expr = expr.replace("%", ".*");
        return val.toLowerCase().matches(expr);
    }
}
