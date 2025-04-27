/*
 * @(#) Expr.java	version 2.0   10/7/2019
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

import java.util.Map;

/**
 * Interface for evaluating a logical (boolean) expression.
 *
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 4/7/2019; adjusted and expanded to TripleGeo functionality for thematic filtering against input datasets
 * Last modified: 10/7/2019
 */
public interface Expr {

    /**
     * Evaluates the expression against the given input data.
     *
     * @param data A data record containing the attribute names (key) and their values.
     * @return boolean
     */
    public boolean evaluate(Map<String, String> data);
}