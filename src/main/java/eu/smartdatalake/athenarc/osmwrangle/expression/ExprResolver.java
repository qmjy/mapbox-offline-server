/*
 * @(#) ExprResolver.java	version 2.0   11/7/2019
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the user-specified logical expression and identifies its constituent sub-expressions and logical operators.
 * @author Kostas Patroumpas
 * @version 2.0
 */
/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 4/7/2019; adjusted and expanded to TripleGeo functionality for thematic filtering against input datasets
 * Last modified: 11/7/2019
 */
public class ExprResolver {

	//Specification of the tokens recognizable to a logical expression
	private static final Pattern TOKENS = Pattern.compile("(\\s+)|(AND)|(OR)|(=)|(<>)|(LIKE)|(<)|(>)|(<=)|(>=)|(\\()|(\\))|([\\w\\[.\\]\\\\]+)|\'([^\']+)\'");

	/**
	 * Constructor of the class.
	 */
	public ExprResolver() {}
	
	/**
	 * Tokenizes an input logical expression.
	 * @param input   The input logical expression. Boolean operators include AND, OR, but NOT is not allowed.
	 * @return  A tokenized expression that has recognized logical operators and su-expressions (specified in parentheses).
	 * @throws ParseException  Thrown in case that parsing of input expression has failed.
	 */
	public TokenStream tokenize(String input) throws ParseException {
		
	    Matcher matcher = TOKENS.matcher(input);
	    List<Token> tokens = new ArrayList<>();
	    int offset = 0;
	    TokenType[] types = TokenType.values();
	    while (offset != input.length()) 
	    {
	        if (!matcher.find() || matcher.start() != offset) 
	        {
	            throw new ParseException("Unexpected token at " + offset, offset);
	        }
	        //Identify any tokens as specified in the allowed types
	        for (int i = 0; i < types.length; i++) 
	        {
	            if (matcher.group(i + 1) != null) 
	            {
	                if (types[i] != TokenType.WHITESPACE)
	                    tokens.add(new Token(types[i], offset, matcher.group(i + 1)));
	                break;
	            }
	        }
	        offset = matcher.end();
	    }
	    tokens.add(new Token(TokenType.EOF, input.length(), ""));
	    
	    return new TokenStream(tokens);         //Detected tokens
	}
	

	/**
	 * Parses a tokenized logical expression.
	 * @param stream   The input composed as a list of recognized tokens, possibly including logical operators (AND, OR).
	 * @return  The parsed expression that recognizes sub-expressions and logical operators.
	 * @throws ParseException   Thrown in case that parsing of input expression has failed.
	 */
	public Expr parse(TokenStream stream) throws ParseException {
	    OrExpr expr = new OrExpr(stream);
	    stream.consume(TokenType.EOF); 			//Ensure that the entire input expression has been parsed
	    return expr;
	}
	
}
