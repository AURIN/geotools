/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2008-2016, Open Source Geospatial Foundation (OSGeo)
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
package org.geotools.data.wfs.integration.v1_1;

import static org.junit.Assert.assertEquals;
import static org.geotools.data.wfs.WFSTestData.url;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.XMLAssert;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.ows.HTTPResponse;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.wfs.TestHttpResponse;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.data.wfs.WFSTestData;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.feature.NameImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.ows.ServiceException;
import org.junit.Test;
import org.junit.Before;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.identity.FeatureId;
import org.xml.sax.SAXException;

public class ArcGISIntegrationTest {

  //  private Name typeName = new NameImpl("SLIP_Public_Services_Environment_WFS",
// "SLIP_Public_Services_Environment_WFS_Clearing_Regulations_-_Environmentally_Sensitive_Areas__DER-016_");
  private Name typeName = new NameImpl("SLIP_Public_Services_Environment_WFS",
    "Clearing_Regulations_-_Environmentally_Sensitive_Areas__DER-016_");
  //  private Name typeName2 = new NameImpl("https://services.slip.wa.gov.au/public/services/SLIP_Public_Services/Environment_WFS/MapServer/WFSServer",
  private Name typeName2 = new NameImpl(null,
    "SLIP_Public_Services_Environment_WFS_Clearing_Regulations_-_Environmentally_Sensitive_Areas__DER-016_");
  private Name typeName3 = new NameImpl("https://services.slip.wa.gov.au/arcgis/services/SLIP_Public_Services/Environment_WFS/MapServer/WFSServer",
    "SLIP_Public_Services_Environment_WFS:Clearing_Regulations_-_Environmentally_Sensitive_Areas__DER-016_");
  private WFSDataStore wfs;
// private Name typeName = new NameImpl("SLIP_Public_Services_Environment_WFS",
//  "Clearing_Regulations_-_Environmentally_Sensitive_Areas__DER-016_");

  private WFSDataStore getWFSDataStore(HTTPClient httpClient) throws IOException, ServiceException {
    URL capabilitiesUrl = new URL("https://services.slip.wa.gov.au/public/services/SLIP_Public_Services/Environment_WFS/MapServer/WFSServer?service=WFS&version=1.1.0&REQUEST=GetCapabilities");

    Map<String, Serializable> params;
    params = new HashMap<String, Serializable>();
    params.put(WFSDataStoreFactory.URL.key, capabilitiesUrl);
    params.put(WFSDataStoreFactory.WFS_STRATEGY.key, "arcgis");
    params.put(WFSDataStoreFactory.PROTOCOL.key, Boolean.FALSE);
    return new WFSDataStoreFactory().createDataStore(params);
  }

  @Before
  public void setUp() throws IOException, ServiceException {
    this.wfs = getWFSDataStore(new SimpleHttpClient());
  }

  @Test
  public void testGetCapabilities() throws Exception {
    String types[] = this.wfs.getTypeNames();
    assertEquals(17, types.length);
    assertEquals("SLIP_Public_Services_Environment_WFS:Clearing_Regulations_-_Environmentally_Sensitive_Areas__DER-016_".replace(":", "_"),
      types[0]);
  }

  @Test
  public void testGetFeatures() throws Exception {
    int count = 0;
    SimpleFeatureIterator iter = this.wfs.getFeatureSource(this.typeName2).getFeatures().features();
    while (iter.hasNext()) {
      SimpleFeature feat = iter.next();
      count++;
    }

    assertEquals(10, count);
  }


  AtomicLong reqHandleSeq = new AtomicLong();

  public String newRequestHandle() {
    StringBuilder handle = new StringBuilder("GeoTools ").append(GeoTools.getVersion())
      .append("(").append(GeoTools.getBuildRevision()).append(") WFS ")
      .append("1.1.0").append(" DataStore @");
    try {
      handle.append(InetAddress.getLocalHost().getHostName());
    } catch (Exception ignore) {
      handle.append("<uknown host>");
    }

    handle.append('#').append(reqHandleSeq.incrementAndGet());
    return handle.toString();
  }

  private void assertXMLEqual(String expectedXmlResource, String actualXml) throws IOException {
    String control = IOUtils.toString(url(expectedXmlResource));
    control = control.replace("${getfeature.handle}", newRequestHandle());
    try {
      XMLAssert.assertXMLEqual(control, actualXml);
    } catch (SAXException e) {
      e.printStackTrace();
      throw new IOException(e);
    }
  }

  @Test
  public void testGetFeaturesByBBox() throws Exception {
    final String[] queryTokens = {"<ogc:BBOX>",
      "<ogc:PropertyName>the_geom</ogc:PropertyName>",
      "<gml:Envelope srsDimension=\"2\" srsName=\"urn:x-ogc:def:crs:EPSG:3857\">",
      "<gml:lowerCorner>4623055.0 815134.0</gml:lowerCorner>",
      "<gml:upperCorner>4629904.0 820740.0</gml:upperCorner>"};
    WFSDataStore wfs = getWFSDataStore(new SimpleHttpClient() {
      @Override
      public HTTPResponse post(URL url, InputStream postContent, String postContentType) throws IOException {
        String request = new String(IOUtils.toByteArray(postContent), "UTF-8");
        return super.post(url, new ByteArrayInputStream(request.getBytes("UTF-8")), postContentType);
      }
    });

    SimpleFeatureSource source = wfs.getFeatureSource(typeName);
    SimpleFeature sf = getSampleSimpleFeature(source);

    FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    PropertyName bboxProperty = ff.property(sf.getDefaultGeometryProperty().getName());
    Query query = new Query(typeName.getLocalPart(), ff.bbox(bboxProperty, sf.getBounds()));
    iterate(source.getFeatures(query), 6, true);
  }

  private SimpleFeature getSampleSimpleFeature(SimpleFeatureSource source) throws IOException {
    FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    Set<FeatureId> fids = new HashSet<FeatureId>();
    fids.add(new FeatureIdImpl("comuni11.2671"));
    Query query = new Query(typeName.getLocalPart(), ff.id(fids));
    SimpleFeatureIterator reader = source.getFeatures(query).features();
    try {
      return reader.next();
    } finally {
      reader.close();
    }
  }

  private boolean isHitsRequest(String text, String... tokens) {
    return stringContains(text,
      "<wfs:GetFeature",
      "resultType=\"hits\"",
      "<wfs:Query srsName=\"urn:ogc:def:crs:EPSG::3857\" typeName=\"comuni:comuni11\"") &&
      stringContains(text, tokens);
  }

  private boolean isResultsRequest(String text, String... tokens) {
    return stringContains(text,
      "<wfs:GetFeature",
      "resultType=\"results\"",
      "<wfs:Query srsName=\"urn:ogc:def:crs:EPSG::3857\" typeName=\"comuni:comuni11\"") &&
      stringContains(text, tokens);
  }

  private boolean isDescribeFeatureRequest(String text) {
    return stringContains(text, "<wfs:DescribeFeatureType");
  }

  private boolean stringContains(String text, String... tokens) {
    for (String token : tokens) {
      if (!text.contains(token)) {
        return false;
      }
    }
    return true;
  }

  private static SimpleFeature iterate(SimpleFeatureCollection features, int expectedSize, boolean getSize) {
    int size = -1;
    if (getSize) {
      size = features.size();
      if (size > -1) {
        assertEquals(expectedSize, size);
      }
    }

    size = 0;
    SimpleFeatureIterator reader = features.features();
    SimpleFeature sf = null;
    try {
      while (reader.hasNext()) {
        if (sf == null) {
          sf = reader.next();
        } else {
          reader.next().getIdentifier();
        }
        size++;
      }
    } finally {
      reader.close();
    }

    assertEquals(expectedSize, size);

    return sf;
  }
}
