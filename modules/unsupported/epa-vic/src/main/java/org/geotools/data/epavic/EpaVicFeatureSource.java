package org.geotools.data.epavic;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.geotools.data.DefaultResourceInfo;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.ResourceInfo;
import org.geotools.data.epavic.schema.MeasurementFields;
import org.geotools.data.epavic.schema.Monitors;
import org.geotools.data.epavic.schema.Site;
import org.geotools.data.epavic.schema.Sites;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.geotools.gce.imagemosaic.Utils.BBOXFilterExtractor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.And;
import org.opengis.filter.Filter;
import org.opengis.filter.Or;
import org.opengis.filter.PropertyIsBetween;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

/**
 * Source of features for the ArcGIS ReST API
 *
 * @author lmorandini
 */
public class EpaVicFeatureSource extends ContentFeatureSource {

    // Request parameters
    public static String SITEID = "SiteId";

    public static String MONITORID = "MonitorId";

    public static String TIMEBASEID = "TimeBaseId";

    public static String FROMDATE = "FromDate";

    public static String TODATE = "ToDate";

    public static String BBOXPARAM = "BBOX";

    public static int FILTERREQUIREDPARAMS = 4;

    public static String AURINTIMEFORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    public static String EPATIMEFORMAT = "yyyyMMddHH";

    protected EpaVicDatastore dataStore;

    protected DefaultResourceInfo resInfo;

    protected String objectIdField;

    /**
     * Inner class used to build the request parameters. Only "And" logical connectors with equality
     * can be used
     *
     * @author lmorandini
     */
    protected final class VisitFilter extends DefaultFilterVisitor {

        public Object visit(And expr, Object data) {
            Map<String, String> map = (Map<String, String>) data;

            expr.getChildren()
                    .forEach(
                            (eqExpr) -> {
                                if (eqExpr instanceof And) {
                                    this.visit((And) eqExpr, map);
                                }
                                if (eqExpr instanceof PropertyIsEqualTo) {
                                    this.visit((PropertyIsEqualTo) eqExpr, map);
                                }
                                if (eqExpr instanceof PropertyIsBetween) {
                                    this.visit((PropertyIsBetween) eqExpr, map);
                                }
                            });

            return map;
        }

        public Object visit(Or expr, Object data) {
            return (Map<String, String>) data;
        }

        public Object visit(PropertyIsEqualTo expr, Object data) {

            Map<String, String> map = (Map<String, String>) data;
            map.put(expr.getExpression1().toString(), expr.getExpression2().toString());
            return map;
        }

        public Object visit(PropertyIsBetween expr, Object data) {
            Map<String, String> map = (Map<String, String>) data;
            map.put(FROMDATE, expr.getLowerBoundary().toString());
            map.put(TODATE, expr.getUpperBoundary().toString());
            return map;
        }
    }

    public EpaVicFeatureSource(ContentEntry entry, Query query) {
        super(entry, query);
        this.dataStore = (EpaVicDatastore) entry.getDataStore();
    }

    protected static String composeErrorMessage(Filter filter, String msg) {
        return "The "
                + filter.toString()
                + " CQL expression is incorrect: "
                + msg
                + ". the CQL has to have"
                + " MonitorID, TimeBaseID equality expression and time duration tied by 'And' logical predicate."
                + " Date must be expressed as YYYYMMDD "
                + "As in: \"BBOX(SHAPE, 144.79309207663,-37.790887782994,144.82828265916,-37.766134928431) "
                + "AND MonitorId='PM10' AND TimeBaseId='24HR_RAV' "
                + "AND DateTimeRecorded BETWEEN '2018-03-21T10:00:00' AND '2019-03-23T10:00:00'";
    }

    public static String convertDateFormatBetweenAurinAndEPA(String aurinDate)
            throws ParseException {
        try {
            return (new SimpleDateFormat(EPATIMEFORMAT))
                    .format((new SimpleDateFormat(AURINTIMEFORMAT)).parse(aurinDate));
        } catch (NullPointerException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    @Override
    protected SimpleFeatureType buildFeatureType() throws IOException {

        // Sets the information about the resource
        this.resInfo = new DefaultResourceInfo();
        try {
            this.resInfo.setSchema(new URI(this.dataStore.getNamespace().toExternalForm()));
        } catch (URISyntaxException e) {
            // Re-packages the exception to be compatible with method signature
            throw new IOException(e.getMessage(), e.fillInStackTrace());
        }
        try {
            this.resInfo.setCRS(CRS.decode(EpaVicDatastore.EPACRS));
        } catch (FactoryException e) {
            throw new IllegalStateException(e);
        }

        this.resInfo.setName(EpaVicDatastore.MEASUREMENT);

        try {
            this.schema = buildType();
        } catch (FactoryException e) {
            throw new IOException(e);
        }

        return this.schema;
    }

    public static SimpleFeatureType buildType()
            throws NoSuchAuthorityCodeException, FactoryException {
        // Builds the feature type
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setCRS(CRS.decode(EpaVicDatastore.EPACRS));
        builder.setName(EpaVicDatastore.MEASUREMENT);

        for (MeasurementFields fld : MeasurementFields.values()) {
            builder.add(fld.getFieldName(), fld.getType());
        }

        builder.add(EpaVicDatastore.GEOMETRY_ATTR, Point.class);
        SimpleFeatureType buildFeatureType = builder.buildFeatureType();
        return buildFeatureType;
    }

    @Override
    public ResourceInfo getInfo() {
        if (this.resInfo == null) {
            try {
                this.buildFeatureType();
            } catch (IOException e) {
                this.getDataStore().getLogger().log(Level.SEVERE, e.getMessage(), e);
                return null;
            }
        }
        return this.resInfo;
    }

    @Override
    public ContentDataStore getDataStore() {
        return this.dataStore;
    }

    @Override
    public Name getName() {
        return this.entry.getName();
    }

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query arg0) throws IOException {
        return this.getInfo().getBounds();
    }

    @Override
    protected int getCountInternal(Query query) throws IOException {

        try {
            Queue<InputStream> siteStreams = loadSiteStreams(query, dataStore.retrieveSitesJSON());

            int totalMeasurements = 0;
            for (InputStream inputStream : siteStreams) {
                JsonFactory jfactory = new JsonFactory();
                try (JsonParser jParser = jfactory.createParser(inputStream)) {
                    while (jParser.nextToken() != JsonToken.END_OBJECT) {
                        String fieldname = jParser.getCurrentName();
                        if ("NumberOfMeasurements".equals(fieldname)) {
                            jParser.nextToken();
                            totalMeasurements += jParser.getIntValue();
                        }
                    }
                }
            }
            return totalMeasurements;
        } catch (CQLException | ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(Query query)
            throws IOException {

        try {

            Sites sites = dataStore.retrieveSitesJSON();
            Monitors monitors = dataStore.retrieveMonitorsJSON();

            Queue<InputStream> siteStreams = loadSiteStreams(query, sites);

            // Returns a reader for the result
            return new EpaVicFeatureReader(this.schema, siteStreams, sites, monitors);

        } catch (CQLException | ParseException e) {
            throw new IOException(e);
        }
    }

    private Queue<InputStream> loadSiteStreams(Query query, Sites sites)
            throws CQLException, IOException, ParseException {
        Map<String, Object> params = composeRequestParameters(query.getFilter());
        ReferencedEnvelope bbox = (ReferencedEnvelope) params.get(BBOXPARAM);

        List<Site> sitesToRetrieve = Collections.emptyList();
        if (bbox != null) {
            sitesToRetrieve =
                    sites.getSites()
                            .stream()
                            .filter(site -> bbox.contains(site.getLongitude(), site.getLatitude()))
                            .collect(Collectors.toList());
        } else {
            sitesToRetrieve = sites.getSites();
        }

        Queue<InputStream> siteStreams = new LinkedList<>();
        if (sitesToRetrieve.isEmpty()) {
            siteStreams.add(this.dataStore.retrieveJSON(params));
        } else {
            for (Site s : sitesToRetrieve) {
                Map<String, Object> p = new HashMap<>(params);
                p.put(SITEID, s.getSiteId().toString());
                siteStreams.add(this.dataStore.retrieveJSON(params));
            }
        }
        return siteStreams;
    }

    /**
     * Helper method to return an attribute list as the API expects it
     *
     * @param query Query to build the attributes for
     */
    protected String composeAttributes(Query query) {

        StringJoiner joiner = new StringJoiner(",");

        // The Object ID is always in to ensure the GeoJSON is correctly processed
        // by the parser,
        // For instance, when the GeoJSON properties is null (i.e., only the
        // geometry is
        // returned), WMS GetMap requests return an empty image
        joiner.add(this.objectIdField);

        if (query.retrieveAllProperties()) {
            Iterator<AttributeDescriptor> iter = this.schema.getAttributeDescriptors().iterator();
            while (iter.hasNext()) {
                AttributeDescriptor attr = iter.next();
                // Skips ID and geometry field
                if (!attr.getLocalName().equalsIgnoreCase(this.objectIdField)
                        && !attr.getLocalName()
                                .equalsIgnoreCase(
                                        this.schema.getGeometryDescriptor().getLocalName())) {
                    joiner.add(iter.next().getLocalName());
                }
            }
        } else {
            for (String attr : query.getPropertyNames()) {
                // Skips ID and geometry field
                if (!attr.equalsIgnoreCase(this.objectIdField)
                        && !attr.equalsIgnoreCase(
                                this.schema.getGeometryDescriptor().getLocalName())) {
                    joiner.add(attr);
                }
            }
        }

        return joiner.toString();
    }

    /**
     * Returns a map of KVP parameters extracted from the ECQL Filter
     *
     * @param filter ECQL Filter
     * @return Map of parameters and values
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> composeRequestParameters(Filter filter)
            throws CQLException, ParseException {

        Map<String, Object> requestParams = null;
        BBOXFilterExtractor bboxExtractor = (new BBOXFilterExtractor());

        try {
            requestParams =
                    (Map<String, Object>)
                            filter.accept(
                                    new EpaVicFeatureSource.VisitFilter(),
                                    new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER));
            filter.accept(bboxExtractor, null);
            BoundingBox bbox = bboxExtractor.getBBox();
            if (bbox != null) {
                requestParams.put(BBOXPARAM, bbox);
            }
        } catch (Exception e) {
            throw new CQLException(composeErrorMessage(filter, "CQL expression is incorrect"));
        }

        // Checks that all required parameters are present
        if (requestParams.size() < FILTERREQUIREDPARAMS) {
            throw new CQLException(composeErrorMessage(filter, "There are too few parameters"));
        }

        // Checks that no parameter other than the allowed ones is present
        try {
            requestParams.forEach(
                    (k, v) -> {
                        if (!(BBOXPARAM.equalsIgnoreCase(k)
                                || MONITORID.equalsIgnoreCase(k)
                                || TIMEBASEID.equalsIgnoreCase(k)
                                || FROMDATE.equalsIgnoreCase(k)
                                || TODATE.equalsIgnoreCase(k))) {
                            throw new IllegalArgumentException();
                        }
                    });
        } catch (IllegalArgumentException e) {
            throw new CQLException(composeErrorMessage(filter, "Some parameters are missing"));
        }

        // Converts dates into EPA format
        try {
            requestParams.put(
                    FROMDATE,
                    convertDateFormatBetweenAurinAndEPA((String) requestParams.get(FROMDATE)));
            requestParams.put(
                    TODATE,
                    convertDateFormatBetweenAurinAndEPA((String) requestParams.get(TODATE)));
            return requestParams;

        } catch (IllegalArgumentException | ParseException e) {
            throw new CQLException(composeErrorMessage(filter, e.getMessage()));
        }
    }

    @Override
    protected boolean canFilter() {
        return true;
    }
}
