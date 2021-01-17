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
package org.geotools.gml2.bindings;

import static org.junit.Assert.assertNotNull;

import org.geotools.gml2.GML;
import org.geotools.xsd.ElementInstance;
import org.geotools.xsd.Node;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.picocontainer.defaults.DefaultPicoContainer;

public class GMLMultiLineStringPropertyTypeBindingTest extends AbstractGMLBindingTest {
    ElementInstance association;
    ElementInstance geometry;

    public void setUp() throws Exception {
        super.setUp();

        association =
                createElement(
                        GML.NAMESPACE,
                        "myMultiLineStringProperty",
                        GML.MultiLineStringPropertyType,
                        null);
        geometry = createElement(GML.NAMESPACE, "myMultiLineString", GML.MultiLineStringType, null);

        container = new DefaultPicoContainer();
        container.registerComponentImplementation(GeometryFactory.class);
        container.registerComponentImplementation(GMLGeometryAssociationTypeBinding.class);
        container.registerComponentImplementation(GMLMultiLineStringPropertyTypeBinding.class);
    }

    @Test
    public void testWithGeometry() throws Exception {
        LineString p1 =
                new GeometryFactory()
                        .createLineString(
                                new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1)});
        LineString p2 =
                new GeometryFactory()
                        .createLineString(
                                new Coordinate[] {new Coordinate(2, 2), new Coordinate(3, 3)});

        Node node =
                createNode(
                        association,
                        new ElementInstance[] {geometry},
                        new Object[] {
                            new GeometryFactory().createMultiLineString(new LineString[] {p1, p2})
                        },
                        null,
                        null);

        GMLGeometryAssociationTypeBinding s =
                (GMLGeometryAssociationTypeBinding)
                        container.getComponentInstanceOfType(
                                GMLGeometryAssociationTypeBinding.class);

        GMLMultiLineStringPropertyTypeBinding s1 =
                (GMLMultiLineStringPropertyTypeBinding)
                        container.getComponentInstanceOfType(
                                GMLMultiLineStringPropertyTypeBinding.class);

        MultiLineString p =
                (MultiLineString) s1.parse(association, node, s.parse(association, node, null));
        assertNotNull(p);
    }
}
