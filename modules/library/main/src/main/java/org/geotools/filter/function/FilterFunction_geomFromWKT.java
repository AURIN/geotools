/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2005-2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.filter.function;

// this code is autogenerated - you shouldnt be modifying it!

import static org.geotools.filter.capability.FunctionNameImpl.parameter;

import org.geotools.filter.FunctionExpressionImpl;
import org.geotools.filter.capability.FunctionNameImpl;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.capability.FunctionName;

public class FilterFunction_geomFromWKT extends FunctionExpressionImpl {

    public static FunctionName NAME =
            new FunctionNameImpl(
                    "geomFromWKT", Geometry.class, parameter("geometry", String.class));

    public FilterFunction_geomFromWKT() {
        super("geomFromWKT");
        functionName = NAME;
    }

    public Object evaluate(Object feature) {
        String arg0;

        try { // attempt to get value and perform conversion
            arg0 = getExpression(0).evaluate(feature, String.class); // extra
            // protection
            // for
            // strings
        } catch (Exception e) // probably a type error
        {
            throw new IllegalArgumentException(
                    "Filter Function problem for function geomFromWKT argument #0 - expected type String");
        }

        return (StaticGeometry.geomFromWKT(arg0));
    }
}
