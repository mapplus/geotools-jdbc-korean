/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2008, Open Source Geospatial Foundation (OSGeo)
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
package org.geotools.data.tibero;

import java.io.IOException;
import java.io.Writer;

import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.filter.FilterCapabilities;
import org.geotools.jdbc.SQLDialect;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Beyond;
import org.opengis.filter.spatial.BinarySpatialOperator;
import org.opengis.filter.spatial.Contains;
import org.opengis.filter.spatial.Crosses;
import org.opengis.filter.spatial.DWithin;
import org.opengis.filter.spatial.Disjoint;
import org.opengis.filter.spatial.DistanceBufferOperator;
import org.opengis.filter.spatial.Equals;
import org.opengis.filter.spatial.Intersects;
import org.opengis.filter.spatial.Overlaps;
import org.opengis.filter.spatial.Touches;
import org.opengis.filter.spatial.Within;

@SuppressWarnings("deprecation")
class FilterToSqlHelper {

    protected static final String IO_ERROR = "io problem writing filter";

    FilterToSQL delegate;

    Writer out;

    boolean looseBBOXEnabled;

    public FilterToSqlHelper(FilterToSQL delegate) {
        this.delegate = delegate;
    }

    public FilterCapabilities createFilterCapabilities() {
        FilterCapabilities caps = new FilterCapabilities();
        caps.addAll(SQLDialect.BASE_DBMS_CAPABILITIES);

        // adding the spatial filters support
        caps.addType(BBOX.class);
        caps.addType(Contains.class);
        caps.addType(Crosses.class);
        caps.addType(Disjoint.class);
        caps.addType(Equals.class);
        caps.addType(Intersects.class);
        caps.addType(Overlaps.class);
        caps.addType(Touches.class);
        caps.addType(Within.class);

        caps.addType(DWithin.class);
        caps.addType(Beyond.class);

        return caps;
    }

    protected Object visitBinarySpatialOperator(BinarySpatialOperator filter,
            PropertyName property, Literal geometry, boolean swapped, Object extraData) {
        try {
            if (filter instanceof DistanceBufferOperator) {
                visitDistanceSpatialOperator((DistanceBufferOperator) filter, property, geometry,
                        swapped, extraData);
            } else {
                visitComparisonSpatialOperator(filter, property, geometry, swapped, extraData);
            }
        } catch (IOException e) {
            throw new RuntimeException(IO_ERROR, e);
        }
        return extraData;
    }

    void visitDistanceSpatialOperator(DistanceBufferOperator filter, PropertyName property,
            Literal geometry, boolean swapped, Object extraData) throws IOException {
        // NOTE: Tibero does not support DWithin, Beyond

        if ((filter instanceof DWithin && !swapped) || (filter instanceof Beyond && swapped)) {
            out.write("ST_Distance(");
            property.accept(delegate, extraData);
            out.write(",");
            geometry.accept(delegate, extraData);
            out.write(") <= ");
            out.write(Double.toString(filter.getDistance()));
        }

        if ((filter instanceof DWithin && swapped) || (filter instanceof Beyond && !swapped)) {
            out.write("ST_Distance(");
            property.accept(delegate, extraData);
            out.write(",");
            geometry.accept(delegate, extraData);
            out.write(") > ");
            out.write(Double.toString(filter.getDistance()));
        }
    }

    void visitComparisonSpatialOperator(BinarySpatialOperator filter, PropertyName property,
            Literal geometry, boolean swapped, Object extraData) throws IOException {

        // NOTE: Tibero Spatial returns TRUE(1) or FALSE(0)
        String closingParenthesis = ")=1";

        if (filter instanceof Equals) {
            out.write("ST_Equals");
        } else if (filter instanceof Disjoint) {
            out.write("NOT (ST_Intersects");
            closingParenthesis += ")";
        } else if (filter instanceof Intersects || filter instanceof BBOX) {
            out.write("ST_Intersects");
        } else if (filter instanceof Crosses) {
            out.write("ST_Crosses");
        } else if (filter instanceof Within) {
            if (swapped)
                out.write("ST_Contains");
            else
                out.write("ST_Within");
        } else if (filter instanceof Contains) {
            if (swapped)
                out.write("ST_Within");
            else
                out.write("ST_Contains");
        } else if (filter instanceof Overlaps) {
            out.write("ST_Overlaps");
        } else if (filter instanceof Touches) {
            out.write("ST_Touches");
        } else {
            throw new RuntimeException("Unsupported filter type " + filter.getClass());
        }
        out.write("(");

        property.accept(delegate, extraData);
        out.write(", ");
        geometry.accept(delegate, extraData);

        out.write(closingParenthesis);
    }
}
