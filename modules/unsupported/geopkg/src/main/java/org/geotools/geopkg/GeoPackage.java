package org.geotools.geopkg;

import static java.lang.String.format;
import static org.geotools.sql.SqlUtil.prepare;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.IOUtils;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.data.DataStore;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.jdbc.datasource.ManageableDataSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.factory.Hints;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.referencing.CRS;
import org.geotools.sql.SqlUtil;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.coverage.grid.GridCoverageWriter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Provides access to a GeoPackage database.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class GeoPackage {

    static final Logger LOGGER = Logging.getLogger("org.geotools.geopkg");

//    static {
//        //load the sqlite jdbc driver up front
//        try {
//            Class.forName("org.sqlite.JDBC");
//        } catch (ClassNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//    }
    
    public static final String GEOPACKAGE_CONTENTS = "gpkg_contents";

    public static final String GEOMETRY_COLUMNS = "gpkg_geometry_columns";
    
    public static final String SPATIAL_REF_SYS = "gpkg_spatial_ref_sys";
    
    public static final String RASTER_COLUMNS = "gpkg_data_columns";
    
    public static final String TILE_MATRIX_METADATA = "gpkg_tile_matrix";

    public static final String METADATA = "gpkg_metadata";
    
    public static final String METADATA_REFERENCE = "gpkg_metadata_reference";
    
    public static final String TILE_MATRIX_SET = "gpkg_tile_matrix_set";
    
    public static final String DATA_COLUMN_CONSTRAINTS = "gpkg_data_column_constraints";
    
    public static final String EXTENSIONS = "gpkg_extensions";
    
    public static enum DataType {
        Feature("features"), Raster("rasters"), Tile("tiles"), 
        FeatureWithRaster("featuresWithRasters");
    
        String value;
        DataType(String value) {
            this.value = value;
        }
    
        public String value() {
            return value;
        }
    }

    static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-mm-dd'T'HH:MM:ss.SSS'Z'");
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * database file
     */
    File file;

    /** 
     * connection pool
     */
    final DataSource connPool;

    /** 
     * datastore for vector access, lazily created
     */
    volatile JDBCDataStore dataStore;

    /**
     * Creates a new empty GeoPackage, generating a new file.
     */
    public GeoPackage() throws IOException {
        this(File.createTempFile("geopkg", "db"));
    }

    /**
     * Creates a GeoPackage from an existing file.
     * <p>
     * This constructor assumes no credentials are required to connect to the database. 
     * </p>
     */
    public GeoPackage(File file) throws IOException {
        this(file, null, null);
    }

    /**
     * Creates a GeoPackage from an existing file specifying database credentials. 
     */
    public GeoPackage(File file, String user, String passwd) throws IOException {
        this.file = file;

        Map params = new HashMap();
        if (user != null) {
            params.put(GeoPkgDataStoreFactory.USER.key, user);
        }
        if (passwd != null) {
            params.put(GeoPkgDataStoreFactory.PASSWD.key, passwd);
        }

        params.put(GeoPkgDataStoreFactory.DATABASE.key, file.getPath());
        params.put(GeoPkgDataStoreFactory.DBTYPE.key, GeoPkgDataStoreFactory.DBTYPE.sample);

        this.connPool = new GeoPkgDataStoreFactory().createDataSource(params);
    }

    GeoPackage(DataSource dataSource) {
        this.connPool = dataSource;
    }

    GeoPackage(JDBCDataStore dataStore) {
        this.dataStore = dataStore;
        this.connPool = dataStore.getDataSource();
    }

    /**
     * The underlying database file.
     * <p>
     * Note: this value may be <code>null</code> depending on how the geopackage was initialized. 
     * </p>
     */
    public File getFile() {
        return file;
    }

    /**
     * The database data source.
     */
    public DataSource getDataSource() {
        return connPool;
    }

    /**
     * Initializes the geopackage database.
     * <p>
     * This method creates all the necessary metadata tables.
     * </p> 
     */
    public void init() throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                init(cx);
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Initializes a geopackage connection.
     * <p>
     * This method creates all the necessary metadata tables.
     * </p> 
     */
    void init(Connection cx) throws SQLException {
        
        //runSQL("SELECT InitSpatialMetaData();");
        runScript(SPATIAL_REF_SYS + ".sql", cx);
        runScript(GEOMETRY_COLUMNS + ".sql", cx);
        runScript(GEOPACKAGE_CONTENTS + ".sql", cx);
        runScript(TILE_MATRIX_SET +".sql", cx);
        runScript(TILE_MATRIX_METADATA + ".sql", cx);
        runScript(RASTER_COLUMNS + ".sql", cx);
        runScript(METADATA + ".sql", cx);
        runScript(METADATA_REFERENCE + ".sql", cx);
        runScript(DATA_COLUMN_CONSTRAINTS + ".sql", cx);
        runScript(EXTENSIONS + ".sql", cx);
    }

    /**
     * Closes the geopackage database connection.
     * <p>
     * The application should always call this method when done with a geopackage to 
     * prevent connection leakage. 
     * </p>
     */
    public void close() {
        if (dataStore != null) {
            dataStore.dispose();
        }

        try {
            if (connPool instanceof BasicDataSource) {
                ((BasicDataSource) connPool).close();
            }
            else if (connPool instanceof ManageableDataSource) {
                ((ManageableDataSource) connPool).close();
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error closing database connection", e);
        }
    }

    /**
     * Adds an epsg crs to the geopackage, registering it in the spatial_ref_sys table.
     * <p>
     * This method will look up the <tt>srid</tt> in the local epsg database. Use 
     * {@link #addCRS(CoordinateReferenceSystem, int)} to specify an explicit CRS, authority, code
     * entry. 
     * </p>
     */
    public void addCRS(int srid) throws IOException {
        try {
            addCRS(CRS.decode("EPSG:" + srid), "epsg", srid);
        } 
        catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Adds a crs to the geopackage, registring it in the spatial_ref_sys table.
     *  
     * @param crs The crs to add.
     * @param auth The authority code, example: epsg
     * @param srid The spatial reference system id.
     * 
     */
    public void addCRS(CoordinateReferenceSystem crs, String auth, int srid) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                PreparedStatement ps = cx.prepareStatement(String.format(
                    "SELECT srs_id FROM %s WHERE srs_id = ?", SPATIAL_REF_SYS));
                try {
                    ResultSet rs = prepare(ps).set(srid).log(Level.FINE)
                        .statement().executeQuery();
                    try {
                        if (rs.next()) {
                            return;
                        }
                    } finally {
                        close(rs);
                    }
                }
                finally {
                    close(ps);
                }
                
                ps = cx.prepareStatement(String.format(
                    "INSERT INTO %s (srs_id, srs_name, organization, organization_coordsys_id, definition) VALUES (?,?,?,?,?)", 
                    SPATIAL_REF_SYS)); 
                try {
                    prepare(ps)
                        .set(srid)
                        .set(crs.getName().toString())
                        .set(auth)
                        .set(srid)
                        .set(crs.toWKT())
                        .log(Level.FINE).statement().execute();
                }
                finally {
                    close(ps);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns list of contents of the geopackage. 
     */
    public List<Entry> contents() {
        List<Entry> contents = new ArrayList();
        try {
            Connection cx = connPool.getConnection();
            try {
            Statement st = cx.createStatement();
            try {
                ResultSet rs = st.executeQuery("SELECT * FROM " + GEOPACKAGE_CONTENTS);
                try {
                    while(rs.next()) {
                        
                    }
                }
                finally {
                    close(rs);
                }
            }
            finally {
                close(st);
            }
            } 
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new RuntimeException(e);
        }
        return contents;
    }
//
//    //
//    // feature methods
//    //
//
    /**
     * Lists all the feature entries in the geopackage. 
     *
     */
    public List<FeatureEntry> features() throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                List<FeatureEntry> entries = new ArrayList();
                String sql = format(
                "SELECT a.*, b.column_name, b.geometry_type_name, b.z, b.m, c.organization_coordsys_id, c.definition" +
                 " FROM %s a, %s b, %s c" + 
                " WHERE a.table_name = b.table_name" + 
                  " AND a.srs_id = c.srs_id" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, GEOMETRY_COLUMNS, SPATIAL_REF_SYS);
                PreparedStatement ps = cx.prepareStatement(sql);
                try {
                    ps.setString(1, DataType.Feature.value());
                    ResultSet rs = ps.executeQuery();
                    try {
                        while(rs.next()) {
                            entries.add(createFeatureEntry(rs));
                        }
                    } finally {
                        close(rs);
                    }
                } finally {
                    close(ps);
                }

                return entries;
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Looks up a feature entry by name.
     * 
     * @param name THe name of the feature entry.
     * @return The entry, or <code>null</code> if no such entry exists.
     */
    public FeatureEntry feature(String name) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, b.column_name, b.geometry_type_name, b.m, b.z, c.organization_coordsys_id, c.definition" +
                 " FROM %s a, %s b, %s c" + 
                " WHERE a.table_name = b.table_name " + 
                  " AND a.srs_id = c.srs_id " +
                  " AND a.table_name = ?" +
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, GEOMETRY_COLUMNS, SPATIAL_REF_SYS);

                PreparedStatement ps = cx.prepareStatement(sql);
                try {
                    ps.setString(1, name);
                    ps.setString(2, DataType.Feature.value());

                    ResultSet rs = ps.executeQuery();

                    try {
                        if(rs.next()) {
                            return createFeatureEntry(rs);
                        }
                    }
                    finally {
                        close(rs);
                    }
                }
                finally {
                    close(ps);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
        return null;
    }

    /**
     * Creates a new feature entry in the geopackage.
     * <p>
     * The resulting feature dataset will be empty. The 
     * {@link #writer(FeatureEntry, boolean, Transaction)} method returns a writer object that can 
     * be used to populate the dataset. 
     * </p>
     * @param entry Contains metadata about the feature entry.
     * @param schema The schema of the feature dataset.
     * 
     * @throws IOException Any errors occurring while creating the new feature entry.  
     */
    public void create(FeatureEntry entry, SimpleFeatureType schema) throws IOException {
        //clone entry so we can work on it
        FeatureEntry e = new FeatureEntry();
        e.init(entry);
        e.setTableName(schema.getTypeName());

        if (e.getGeometryColumn() != null) {
            //check it
            if (schema.getDescriptor(e.getGeometryColumn()) == null) {
                throw new IllegalArgumentException(
                    format("Geometry column %s does not exist in schema", e.getGeometryColumn()));
            }
        }
        else {
            e.setGeometryColumn(findGeometryColumn(schema));
        }

        if (e.getIdentifier() == null) {
            e.setIdentifier(schema.getTypeName());
        }
        if (e.getDescription() == null) {
            e.setDescription(e.getIdentifier());
        }

        //check for bounds
        if (e.getBounds() == null) {
            throw new IllegalArgumentException("Entry must have bounds");
        }

        //check for srid
        if (e.getSrid() == null) {
            try {
                e.setSrid(findSRID(schema));
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        if (e.getSrid() == null) {
            throw new IllegalArgumentException("Entry must have srid");
        }

        if (e.getGeometryType() == null) {
            e.setGeometryType(findGeometryType(schema));
        }
        //mark changed
        e.setLastChange(new Date());

        //pass in teh feature entry to the datsatore as user data
        schema.getUserData().put(FeatureEntry.class, e);

        JDBCDataStore dataStore = dataStore();

        //create the feature table
        dataStore.createSchema(schema);

        //update the metadata tables
        //addGeoPackageContentsEntry(e);

        //update the entry
        entry.init(e);
    }

    /**
     * Adds a new feature dataset to the geopackage.
     *
     * @param entry Contains metadata about the feature entry.
     * @param collection The simple feature collection to add to the geopackage. 
     * 
     * @throws IOException Any errors occurring while adding the new feature dataset.  
     */
    public void add(FeatureEntry entry, SimpleFeatureCollection collection) throws IOException {
        FeatureEntry e = new FeatureEntry();
        e.init(entry);

        if (e.getBounds() == null) {
            e.setBounds(collection.getBounds());
        }

        create(e, collection.getSchema());

        Transaction tx = new DefaultTransaction();
        SimpleFeatureWriter w = writer(e, true, null, tx);
        SimpleFeatureIterator it = collection.features();
        try {
            while(it.hasNext()) {
                SimpleFeature f = it.next(); 
                SimpleFeature g = w.next();
                for (PropertyDescriptor pd : collection.getSchema().getDescriptors()) {
                    String name = pd.getName().getLocalPart();
                    g.setAttribute(name, f.getAttribute(name));
                }
                                             
                w.write();
            }
            tx.commit();
        }
        catch(Exception ex) {
            tx.rollback();
            throw new IOException(ex);
        }
        finally {
            w.close();
            it.close();
            tx.close();
        }

        entry.init(e);
    }

    /**
     * Adds a new feature dataset to the geopackage.
     *
     * @param entry Contains metadata about the feature entry.
     * @param source The dataset to add to the geopackage.
     * @param filter Filter specifying what subset of feature dataset to include, may be 
     *  <code>null</code> to specify no filter. 
     * 
     * @throws IOException Any errors occurring while adding the new feature dataset.  
     */
    public void add(FeatureEntry entry, SimpleFeatureSource source, Filter filter) throws IOException {
        
        //copy over features
        //TODO: make this more robust, won't handle case issues going between datasources, etc...
        //TODO: for big datasets we need to break up the transaction
        if (filter == null) {
            filter = Filter.INCLUDE;
        }

        add(entry, source.getFeatures(filter));
    }

    /**
     * Returns a writer used to modify or add to the contents of a feature dataset.
     *  
     * @param entry The feature entry. 
     * @param append Flag controlling whether to modify existing contents, or append to the dataset. 
     * @param filter Filter determining what subset of dataset to modify, only relevant when 
     *   <tt>append</tt> set to false. May be <code>null</code> to specify no filter.
     * @param tx Transaction object, may be <code>null</code> to specify auto commit transaction.
     *
     */
    public SimpleFeatureWriter writer(FeatureEntry entry, boolean append, Filter filter, 
        Transaction tx) throws IOException {

        DataStore dataStore = dataStore();
        FeatureWriter w = append ?  dataStore.getFeatureWriterAppend(entry.getTableName(), tx) 
            : dataStore.getFeatureWriter(entry.getTableName(), filter, tx);

        return Features.simple(w);
    }

    /**
     * Returns a reader for the contents of a feature dataset.
     * 
     * @param entry The feature entry.
     * @param filter Filter Filter determining what subset of dataset to return. May be 
     *   <code>null</code> to specify no filter.
     * @param tx Transaction object, may be <code>null</code> to specify auto commit transaction.
     */
    public SimpleFeatureReader reader(FeatureEntry entry, Filter filter, Transaction tx) throws IOException {
        Query q = new Query(entry.getTableName());
        q.setFilter(filter != null ? filter : Filter.INCLUDE);

        return Features.simple(dataStore().getFeatureReader(q, tx));
    }

    static Integer findSRID(SimpleFeatureType schema) throws Exception {
        CoordinateReferenceSystem crs = schema.getCoordinateReferenceSystem();
        if (crs == null) {
            GeometryDescriptor gd = findGeometryDescriptor(schema);
            crs = gd.getCoordinateReferenceSystem();
        }

        return crs != null ? CRS.lookupEpsgCode(crs, true) : null;
    }

    static String findGeometryColumn(SimpleFeatureType schema) {
        GeometryDescriptor gd = findGeometryDescriptor(schema);
        return gd != null ? gd.getLocalName() : null;
    }

    static Geometries findGeometryType(SimpleFeatureType schema) {
        GeometryDescriptor gd = findGeometryDescriptor(schema);
        return gd != null ? 
            Geometries.getForBinding((Class<? extends Geometry>) gd.getType().getBinding()) : null;
    }

    static GeometryDescriptor findGeometryDescriptor(SimpleFeatureType schema) {
        GeometryDescriptor gd = schema.getGeometryDescriptor();
        if (gd == null) {
            for (PropertyDescriptor pd : schema.getDescriptors()) {
                if (pd instanceof GeometryDescriptor) {
                    return (GeometryDescriptor) pd;
                }
            }
        }
        return gd;
    }

    FeatureEntry createFeatureEntry(ResultSet rs) throws SQLException, IOException {
        FeatureEntry e = new FeatureEntry();
        initEntry(e, rs);

        e.setGeometryColumn(rs.getString("column_name"));
        e.setGeometryType(Geometries.getForName(rs.getString("geometry_type_name")));
        e.setZ(rs.getBoolean("z"));
        e.setM(rs.getBoolean("m"));

        return e;
    }

    void addGeoPackageContentsEntry(Entry e) throws IOException {
        addCRS(e.getSrid());

        StringBuilder sb = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        
        sb.append(format("INSERT INTO %s (table_name, data_type, identifier", GEOPACKAGE_CONTENTS));
        vals.append("VALUES (?,?,?");

        if (e.getDescription() != null) {
            sb.append(", description");
            vals.append(",?");
        }

        if (e.getLastChange() != null) {
            sb.append(", last_change");
            vals.append(",?");
        }
        if (e.getBounds() != null) {
            sb.append(", min_x, min_y, max_x, max_y");
            vals.append(",?,?,?,?");
        }
        
        if (e.getSrid() != null) {
            sb.append(", srs_id");
            vals.append(",?");
        }
        sb.append(") ").append(vals.append(")").toString());

        try {
            Connection cx = connPool.getConnection();
            try {
                SqlUtil.PreparedStatementBuilder psb = prepare(cx, sb.toString())
                    .set(e.getTableName())
                    .set(e.getDataType().value())
                    .set(e.getIdentifier());

                if (e.getDescription() != null) {
                    psb.set(e.getDescription());
                }

                if (e.getLastChange() != null) {
                    psb.set(DATE_FORMAT.format(e.getLastChange()));
                }
                if (e.getBounds() != null) {
                    psb.set(e.getBounds().getMinX())
                        .set(e.getBounds().getMinY())
                        .set(e.getBounds().getMaxX())
                        .set(e.getBounds().getMaxY());
                }
                if (e.getSrid() != null) {
                    psb.set(e.getSrid());
                }
                    
                PreparedStatement ps = psb.log(Level.FINE).statement();
                try {
                    ps.execute();
                } 
                finally {
                    close(ps);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException ex) {
            throw new IOException(ex);
        }
    }

    void addGeometryColumnsEntry(FeatureEntry e) throws IOException {
        String sql = format(
                "INSERT INTO %s VALUES (?, ?, ?, ?, ?, ?);", GEOMETRY_COLUMNS);

        try {
            Connection cx = connPool.getConnection();
            try {
                PreparedStatement ps = prepare(cx, sql)
                    .set(e.getTableName())
                    .set(e.getGeometryColumn())
                    .set(e.getGeometryType() != null ? e.getGeometryType().getName():null)                    
                    .set(e.getSrid())
                    .set(e.isZ())                    
                    .set(e.isM())
                    .log(Level.FINE)
                    .statement();
                try {
                    ps.execute();
                }
                finally {
                    close(ps);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException ex) {
            throw new IOException(ex);
        }
    }

    //
    // raster methods
    //

    /**
     * Lists all the raster entries in the geopackage. 
     */
    public List<RasterEntry> rasters() throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                List<RasterEntry> entries = new ArrayList();
                String sql = format(
                "SELECT a.*, b.column_name, b.name, b.title, b.mime_type, b.description column_description, b.constraint_name, c.organization_coordsys_id, c.definition" +
                 " FROM %s a, %s b, %s c" + 
                " WHERE a.table_name = b.table_name" +
                  " AND a.srs_id = c.srs_id " + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, RASTER_COLUMNS, SPATIAL_REF_SYS);
                PreparedStatement ps = cx.prepareStatement(sql);
                try {
                    ps.setString(1, DataType.Raster.value());

                    ResultSet rs = ps.executeQuery();
                    try {
                        while(rs.next()) {
                            entries.add(createRasterEntry(rs));
                        }
                    }
                    finally {
                        close(rs);
                    }
                }
                finally {
                    close(ps);
                }
                return entries;
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Looks up a raster entry by name.
     * 
     * @param name THe name of the raster entry.
     * @return The entry, or <code>null</code> if no such entry exists.
     */
    public RasterEntry raster(String name) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, b.name, b.title, b.mime_type, b.column_name, b.description column_description, b.constraint_name, c.organization_coordsys_id, c.definition" +
                 " FROM %s a, %s b, %s c" + 
                " WHERE a.table_name = b.table_name" +
                  " AND a.srs_id = c.srs_id" + 
                  " AND a.table_name = ?" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, RASTER_COLUMNS, SPATIAL_REF_SYS);
                PreparedStatement ps = cx.prepareStatement(sql);
                try {
                    ps.setString(1, name);
                    ps.setString(2, DataType.Raster.value());

                    ResultSet rs = ps.executeQuery();
                    try {
                        if (rs.next()) {
                            return createRasterEntry(rs);
                        }
                    }
                    finally {
                        close(rs);
                    }
                } 
                finally {
                    close(ps);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }

        return null;
    }

    /**
     * Adds a new raster dataset to the geopackage.
     *
     * @param entry Contains metadata about the raster entry.
     * @param raster The raster dataset.
     * @param format The format in which to store the raster in the database.
     * 
     * @throws IOException Any errors occurring while adding the new feature dataset.
     */
    public void add(RasterEntry entry, GridCoverage2D raster, AbstractGridFormat format) 
        throws IOException {

        RasterEntry e = new RasterEntry();
        e.init(entry);

        if (e.getTableName() == null) {
            if (raster.getName() == null) {
                throw new IllegalArgumentException("No table name specified for raster");
            }
            e.setTableName(raster.getName().toString());
        }

        if (e.getRasterColumn() == null) {
            e.setRasterColumn("raster");
        }

        if (e.getSrid() == null) {
            try {
                e.setSrid(findSRID(raster));
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        if (e.getSrid() == null) {
            throw new IllegalArgumentException("Entry must have srid");
        }

        if (e.getBounds() == null) {
            e.setBounds(findBounds(raster));
        }
        if (e.getBounds() == null) {
            throw new IllegalArgumentException("Entry must have bounds");
        }

        if (e.getIdentifier() == null) {
            e.setIdentifier(raster.getName().toString());
        }
        if (e.getDescription() == null) {
            e.setDescription(e.getIdentifier());
        }

        e.setLastChange(new Date());

        //write out raster to temp file
        File tmpFile = File.createTempFile(e.getTableName(), "raster");

        GridCoverageWriter writer = format.getWriter(tmpFile);
        writer.write(raster, null);
        writer.dispose();

        //create the raster table
        try {
            Connection cx = connPool.getConnection();
            try {
                Statement st = cx.createStatement();
                try {
                    String sql = format("CREATE TABLE %s (id INTEGER PRIMARY KEY AUTOINCREMENT, %s BLOB NOT NULL)", 
                        e.getTableName(), e.getRasterColumn());
                    LOGGER.fine(sql);

                    st.execute(sql);
                }
                finally {
                    close(st);
                }

                //TODO: ideally we would stream this in
                BufferedInputStream bin = new BufferedInputStream(new FileInputStream(tmpFile));
                byte[] blob = IOUtils.toByteArray(bin);

                try {
                    PreparedStatement ps = prepare(cx, 
                        format("INSERT INTO %s (%s) VALUES (?)",e.getTableName(), e.getRasterColumn()))
                    .set(blob).log(Level.FINE).statement();
                    try {
                        ps.execute();
                    } 
                    finally {
                        close(ps);
                    }
                }
                finally {
                    bin.close();
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException ex) {
            throw new IOException(ex);
        }

        tmpFile.delete();

        addGeoPackageContentsEntry(e);
        addRasterColumnsEntry(e);

        entry.init(e);
    }

    /**
     * Returns a reader for the contents of a raster dataset.
     * 
     * @param entry The raster entry.
     * @param format Format of the raster dataset.
     */
    public GridCoverageReader reader(RasterEntry entry, AbstractGridFormat format) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                Statement st = cx.createStatement();
                try {
                    ResultSet rs = st.executeQuery(
                        format("SELECT %s FROM %s;", entry.getRasterColumn(), entry.getTableName()));
                    try {
                        if (rs.next()) {
                            byte[] blob = rs.getBytes(1);
                            Hints hints = new Hints();
                            //if (format instanceof WorldImageFormat) {
                            //    TODO: get this patch submitted
                            //    hints.put(WorldImageFormat.ORIGINAL_ENVELOPE, toGeneralEnvelope(entry.getBounds()));
                            //}
                            return format.getReader(blob, hints);
                        }
                    }
                    finally {
                        close(rs);
                    }
                }
                finally {
                    close(st);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
        return null;
    }

    static Integer findSRID(GridCoverage2D raster) throws Exception {
        return CRS.lookupEpsgCode(raster.getCoordinateReferenceSystem(), true);
    }

    static ReferencedEnvelope findBounds(GridCoverage2D raster) {
        org.opengis.geometry.Envelope e = raster.getEnvelope();
        return new ReferencedEnvelope(e.getMinimum(0), e.getMaximum(0), e.getMinimum(1), 
            e.getMaximum(1), raster.getCoordinateReferenceSystem());
    }

    static GeneralEnvelope toGeneralEnvelope(ReferencedEnvelope e) {
        GeneralEnvelope ge = new GeneralEnvelope(new double[]{e.getMinX(), e.getMinY()}, 
            new double[]{e.getMaxX(), e.getMaxY()});
        ge.setCoordinateReferenceSystem(e.getCoordinateReferenceSystem());
        return ge;
    }

    RasterEntry createRasterEntry(ResultSet rs) throws SQLException, IOException {
        RasterEntry e = new RasterEntry();
        initEntry(e, rs);

        e.setRasterColumn(rs.getString("column_name"));
        e.setName(rs.getString("name"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("column_description"));
        e.setMimeType(rs.getString("mime_type"));
        e.setConstraint(rs.getString("constraint_name"));
        return e;
    }

    void addRasterColumnsEntry(RasterEntry e) throws IOException {
        String sql = format(
                "INSERT INTO %s VALUES (?, ?, ?, ?, ?, ?, ?);", RASTER_COLUMNS);

        try {
            Connection cx = connPool.getConnection();
            try {
                PreparedStatement ps = prepare(cx, sql)
                    .set(e.getTableName())
                    .set(e.getRasterColumn())
                    .set(e.getName())
                    .set(e.getTitle())
                    .set(e.getDescription())
                    .set(e.getMimeType())
                    .set(e.getConstraint())
                    .log(Level.FINE)
                    .statement();
                try {
                    ps.execute();
                }
                finally {
                    close(ps);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException ex) {
            throw new IOException(ex);
        }
    }


    //
    // tile methods
    //

    /**
     * Lists all the tile entries in the geopackage. 
     */
    public List<TileEntry> tiles() throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                List<TileEntry> entries = new ArrayList();
                String sql = format(
                "SELECT a.*, c.organization_coordsys_id, c.definition" +
                 " FROM %s a, %s c" + 
                " WHERE a.srs_id = c.srs_id" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, SPATIAL_REF_SYS);
                LOGGER.fine(sql);

                PreparedStatement ps = cx.prepareStatement(sql);
                try {
                    ps.setString(1, DataType.Tile.value());

                    ResultSet rs = ps.executeQuery();
                    try {
                        while(rs.next()) {
                            entries.add(createTileEntry(rs, cx));
                        }
                    }
                    finally {
                        close(rs);
                    }
                }
                finally {
                    close(ps);
                }
                return entries;
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Looks up a tile entry by name.
     * 
     * @param name THe name of the tile entry.
     * @return The entry, or <code>null</code> if no such entry exists.
     */
    public TileEntry tile(String name) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, c.organization_coordsys_id, c.definition" +
                 " FROM %s a, %s c" +
                  " WHERE a.srs_id = c.srs_id" + 
                  " AND a.table_name = ?" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, SPATIAL_REF_SYS);
                LOGGER.fine(sql);

                PreparedStatement ps = cx.prepareStatement(sql);
                try {
                    ps.setString(1, name);
                    ps.setString(2, DataType.Tile.value());

                    ResultSet rs = ps.executeQuery();
                    try {
                        if(rs.next()) {
                            return createTileEntry(rs, cx);
                        }
                    }
                    finally {
                        close(rs);
                    }
                }
                finally {
                   close(ps);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
        return null;
    }

    /**
     * Creates a new tile entry in the geopackage.
     * 
     * @param entry The tile entry.
     */
    public void create(TileEntry entry) throws IOException {
        if (entry.getBounds() == null) {
            throw new IllegalArgumentException("Tile entry must specify bounds");
        }

        TileEntry e = new TileEntry();
        e.init(entry);

        if (e.getTableName() == null) {
            e.setTableName("tiles");
        }
        if (e.getIdentifier() == null) {
            e.setIdentifier(e.getTableName());
        }
        if (e.getDescription() == null) {
            e.setDescription(e.getIdentifier());
        }

        if (e.getSrid() == null) {
            try {
                e.setSrid(findSRID(entry.getBounds()));
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        e.setLastChange(new Date());

        try {
            Connection cx = connPool.getConnection();
            //TODO: do all of this in a transaction
            try {
                PreparedStatement st;
                
                //add entry to tile matrix set table
                st = prepare(cx, format("INSERT INTO %s VALUES (?,?,?,?,?,?)", TILE_MATRIX_SET))
                        .set(e.getTableName()).set(e.getSrid()).set(e.getBounds().getMinX())
                            .set(e.getBounds().getMinY()).set(e.getBounds().getMaxX()).set(e.getBounds().getMaxY())
                            .statement();
                try {
                    st.execute();
                }
                finally {
                    close(st);
                }
                

                //create the tile_matrix_metadata entries
                st = prepare(cx, format("INSERT INTO %s VALUES (?,?,?,?,?,?,?,?)", TILE_MATRIX_METADATA))
                    .statement();
                try {
                    for (TileMatrix m : e.getTileMatricies()) {
                        prepare(st).set(e.getTableName()).set(m.getZoomLevel()).set(m.getMatrixWidth())
                            .set(m.getMatrixHeight()).set(m.getTileWidth()).set(m.getTileHeight())
                            .set(m.getXPixelSize()).set(m.getYPixelSize())
                            .statement().execute();
                    }
                } 
                finally {
                    close(st);
                }
                //create the tile table itself
                st = cx.prepareStatement(format("CREATE TABLE %s (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "zoom_level INTEGER NOT NULL DEFAULT 0," +
                    "tile_column INTEGER NOT NULL DEFAULT 0," +
                    "tile_row INTEGER NOT NULL DEFAULT 0," +
                    "tile_data BLOB NOT NULL DEFAULT (zeroblob(4)))", e.getTableName()));
                try {
                    st.execute();
                }
                finally {
                    close(st);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException ex) {
            throw new IOException(ex);
        }

        //update the metadata tables
        addGeoPackageContentsEntry(e);

        entry.init(e);
    }


    /**
     * Adds a tile to the geopackage.
     * 
     * @param entry The tile metadata entry.
     * @param tile The tile.
     */
    public void add(TileEntry entry, Tile tile) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                PreparedStatement ps = prepare(cx, format("INSERT INTO %s (zoom_level, tile_column,"
                    + " tile_row, tile_data) VALUES (?,?,?,?)", entry.getTableName()))
                    .set(tile.getZoom()).set(tile.getColumn()).set(tile.getRow()).set(tile.getData())
                    .log(Level.FINE).statement();
                try {
                    ps.execute();
                }
                finally {
                    close(ps);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Retrieve tiles within certain zooms and column/row boundaries
     * 
     * @param entry the tile entry
     * @param lowZoom low zoom boundary
     * @param highZoom high zoom boundary
     * @param lowCol low column boundary
     * @param highCol high column boundary
     * @param lowRow low row boundary
     * @param highRow high row boundary
     * @return
     * @throws IOException
     */
    public TileReader reader(TileEntry entry, Integer lowZoom, Integer highZoom, 
        Integer lowCol, Integer highCol, Integer lowRow, Integer highRow) throws IOException  {

        try {
            List<String> q = new ArrayList();
            if (lowZoom != null) {
                q.add("zoom_level >= " + lowZoom);
            }
            if (highZoom != null) {
                q.add("zoom_level <= " + lowZoom);
            }
            if (lowCol != null) {
                q.add("tile_column >= " + lowCol);
            }
            if (highCol != null) {
                q.add("tile_column <= " + highCol);
            }
            if (lowRow != null) {
                q.add("tile_row >= " + lowRow);
            }
            if (highRow != null) {
                q.add("tile_row <= " + highRow);
            }

            StringBuffer sql = new StringBuffer("SELECT * FROM ").append(entry.getTableName());
            if (!q.isEmpty()) {
                sql.append(" WHERE ");
                for (String s : q) {
                    sql.append(s).append(" AND ");
                }
                sql.setLength(sql.length()-5);
            }

            Connection cx = connPool.getConnection();

            Statement st = cx.createStatement();
            ResultSet rs = st.executeQuery(sql.toString());

            return new TileReader(rs, cx);

        }
        catch(SQLException e) {
            throw new IOException(e);
        }
        
    }
        
    /**
     * Retrieve tile boundaries (min row, max row, min column and max column) for a particular zoom level,
     * available in the actual data
     *  
     * @param entry The tile entry
     * @param zoom the zoom level
     * @param isMax true for max boundary, false for min boundary
     * @param isRow true for rows, false for columns
     * @return the min/max column/row of the zoom level available in the data
     * @throws IOException
     */
    public int getTileBound(TileEntry entry, int zoom, boolean isMax, boolean isRow) throws IOException {
        try {
            
            int tileBounds = -1;
            
            StringBuffer sql = new StringBuffer("SELECT " + (isMax? "MAX" : "MIN") + "( " + (isRow? "tile_row" : "tile_column") + ") FROM ");
            sql.append(entry.getTableName());
            sql.append(" WHERE zoom_level == ");
            sql.append(zoom);
            
            Connection cx = connPool.getConnection();
            try {
                Statement st = cx.createStatement();
                try {
                    ResultSet rs = st.executeQuery(sql.toString());
                    try {
                        rs.next();
                        tileBounds = rs.getInt(1);
                    } 
                    finally {
                        close(rs);
                    }
                } 
                finally {
                   close(st);
                }
            }
            finally {
                close(cx);
            }
            
            return tileBounds;

        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    static TileEntry createTileEntry(ResultSet rs, Connection cx) throws SQLException, IOException {
        TileEntry e = new TileEntry();
        initEntry(e, rs);

        //load all the tile matrix entries
        PreparedStatement psm = cx.prepareStatement(format(
            "SELECT * FROM %s" + 
            " WHERE table_name = ?" +
            " ORDER BY zoom_level ASC", TILE_MATRIX_METADATA));
        try {
            psm.setString(1, e.getTableName());

            ResultSet rsm = psm.executeQuery();
            try {
                while(rsm.next()) {
                    TileMatrix m = new TileMatrix();
                    m.setZoomLevel(rsm.getInt("zoom_level"));
                    m.setMatrixWidth(rsm.getInt("matrix_width"));
                    m.setMatrixHeight(rsm.getInt("matrix_height"));
                    m.setTileWidth(rsm.getInt("tile_width"));
                    m.setTileHeight(rsm.getInt("tile_height"));
                    m.setXPixelSize(rsm.getDouble("pixel_x_size"));
                    m.setYPixelSize(rsm.getDouble("pixel_y_size"));

                    e.getTileMatricies().add(m);
                }
            }
            finally {
                close(rsm);
            }
        }
        finally {
            close(psm);
        }
        return e;
    }

    static Integer findSRID(ReferencedEnvelope e) throws Exception {
        return CRS.lookupEpsgCode(e.getCoordinateReferenceSystem(), true);
    }

    //
    //sql utility methods
    //

    static void initEntry(Entry e, ResultSet rs) throws SQLException, IOException {
        e.setIdentifier(rs.getString("identifier"));
        e.setDescription(rs.getString("description"));
        e.setTableName(rs.getString("table_name"));
        try {
            e.setLastChange(DATE_FORMAT.parse(rs.getString("last_change")));
        } catch (ParseException ex) {
            throw new IOException(ex);
        }

        int srid = rs.getInt("organization_coordsys_id"); 
        e.setSrid(srid);

        CoordinateReferenceSystem crs;
        try {
            crs = CRS.decode("EPSG:" + srid);
        } 
        catch(Exception ex) {
            //try parsing srtext directly
            try {
                crs = CRS.parseWKT(rs.getString("srtext"));
            }
            catch(Exception e2) {
                throw new IOException(ex);
            }
        }

        e.setBounds(new ReferencedEnvelope(rs.getDouble("min_x"), 
            rs.getDouble("max_x"), rs.getDouble("min_y"), rs.getDouble("max_y"), crs));
    }

    static void runSQL(String sql, Connection cx) throws SQLException {
        Statement st = cx.createStatement();
        try {
            st.execute(sql);
        }
        finally {
            close(st);
        }
    }

    void runScript(String filename, Connection cx) throws SQLException{        
        SqlUtil.runScript(getClass().getResourceAsStream(filename), cx);        
    }
    
    private static void close(Connection cx) {
        if (cx != null) {
            try {
                cx.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing connection", e);
            }
        }
    }

    private static void close(Statement  st) {
        if (st != null) {
            try {
                st.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing statement", e);
            }
        }
    }
    
    private static void close(ResultSet  rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing resultset", e);
            }
        }
    }

    JDBCDataStore dataStore() throws IOException {
        if (dataStore == null) {
            synchronized (this) {
                if (dataStore == null) {
                    dataStore = createDataStore();
                }
            }
        }
        return dataStore;
    }

    JDBCDataStore createDataStore() throws IOException {
        Map params = new HashMap();
        params.put(GeoPkgDataStoreFactory.DATASOURCE.key, connPool);
        return new GeoPkgDataStoreFactory().createDataStore(params);
    }
}
