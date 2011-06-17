//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2011 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.feature.persistence.sql.config;

import static javax.xml.XMLConstants.DEFAULT_NS_PREFIX;
import static javax.xml.XMLConstants.NULL_NS_URI;
import static org.deegree.commons.tom.primitive.BaseType.valueOf;
import static org.deegree.feature.types.property.GeometryPropertyType.CoordinateDimension.DIM_2;
import static org.deegree.feature.types.property.GeometryPropertyType.CoordinateDimension.DIM_3;
import static org.deegree.feature.types.property.ValueRepresentation.INLINE;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.deegree.commons.jdbc.ConnectionManager;
import org.deegree.commons.jdbc.QTableName;
import org.deegree.commons.tom.primitive.BaseType;
import org.deegree.commons.tom.primitive.PrimitiveType;
import org.deegree.commons.utils.JDBCUtils;
import org.deegree.commons.utils.Pair;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.feature.persistence.FeatureStoreException;
import org.deegree.feature.persistence.sql.FeatureTypeMapping;
import org.deegree.feature.persistence.sql.GeometryStorageParams;
import org.deegree.feature.persistence.sql.MappedApplicationSchema;
import org.deegree.feature.persistence.sql.expressions.TableJoin;
import org.deegree.feature.persistence.sql.id.AutoIDGenerator;
import org.deegree.feature.persistence.sql.id.FIDMapping;
import org.deegree.feature.persistence.sql.id.IDGenerator;
import org.deegree.feature.persistence.sql.jaxb.AbstractPropertyJAXB;
import org.deegree.feature.persistence.sql.jaxb.FIDMappingJAXB;
import org.deegree.feature.persistence.sql.jaxb.FIDMappingJAXB.ColumnJAXB;
import org.deegree.feature.persistence.sql.jaxb.FeatureTypeJAXB;
import org.deegree.feature.persistence.sql.jaxb.GeometryPropertyJAXB;
import org.deegree.feature.persistence.sql.jaxb.Join;
import org.deegree.feature.persistence.sql.jaxb.SimplePropertyJAXB;
import org.deegree.feature.persistence.sql.rules.GeometryMapping;
import org.deegree.feature.persistence.sql.rules.Mapping;
import org.deegree.feature.persistence.sql.rules.PrimitiveMapping;
import org.deegree.feature.types.FeatureType;
import org.deegree.feature.types.GenericFeatureType;
import org.deegree.feature.types.property.GeometryPropertyType;
import org.deegree.feature.types.property.GeometryPropertyType.CoordinateDimension;
import org.deegree.feature.types.property.GeometryPropertyType.GeometryType;
import org.deegree.feature.types.property.PropertyType;
import org.deegree.feature.types.property.SimplePropertyType;
import org.deegree.filter.expression.PropertyName;
import org.deegree.filter.sql.DBField;
import org.deegree.filter.sql.MappingExpression;
import org.deegree.gml.schema.GMLSchemaInfoSet;
import org.deegree.sqldialect.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates {@link MappedApplicationSchema} instances from JAXB {@link FeatureTypeDecl} instances.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class MappedSchemaBuilderTable extends AbstractMappedSchemaBuilder {

    private static final Logger LOG = LoggerFactory.getLogger( MappedSchemaBuilderTable.class );

    private Map<QName, FeatureType> ftNameToFt = new HashMap<QName, FeatureType>();

    private Map<QName, FeatureTypeMapping> ftNameToMapping = new HashMap<QName, FeatureTypeMapping>();

    private final Connection conn;

    private DatabaseMetaData md;

    // caches the column information
    private Map<String, LinkedHashMap<String, ColumnMetadata>> tableNameToColumns = new HashMap<String, LinkedHashMap<String, ColumnMetadata>>();

    private final SQLDialect dialect;

    /**
     * Creates a new {@link MappedSchemaBuilderTable} instance.
     * 
     * @param jdbcConnId
     *            identifier of JDBC connection, must not be <code>null</code> (used to determine columns / types)
     * @param ftDecls
     *            JAXB feature type declarations, must not be <code>null</code>
     * @throws SQLException
     * @throws FeatureStoreException
     */
    public MappedSchemaBuilderTable( String jdbcConnId, List<FeatureTypeJAXB> ftDecls, SQLDialect dialect )
                            throws SQLException, FeatureStoreException {
        this.dialect = dialect;
        conn = ConnectionManager.getConnection( jdbcConnId );
        try {
            for ( FeatureTypeJAXB ftDecl : ftDecls ) {
                process( ftDecl );
            }
        } finally {
            JDBCUtils.close( conn );
        }
    }

    /**
     * Returns the {@link MappedApplicationSchema} derived from configuration / tables.
     * 
     * @return mapped application schema, never <code>null</code>
     */
    public MappedApplicationSchema getMappedSchema() {
        FeatureType[] fts = ftNameToFt.values().toArray( new FeatureType[ftNameToFt.size()] );
        FeatureTypeMapping[] ftMappings = ftNameToMapping.values().toArray( new FeatureTypeMapping[ftNameToMapping.size()] );
        Map<FeatureType, FeatureType> ftToSuperFt = null;
        Map<String, String> prefixToNs = null;
        GMLSchemaInfoSet xsModel = null;
        // TODO
        GeometryStorageParams geometryParams = new GeometryStorageParams( CRSManager.getCRSRef( "EPSG:4326" ), "-1",
                                                                          CoordinateDimension.DIM_2 );
        return new MappedApplicationSchema( fts, ftToSuperFt, prefixToNs, xsModel, ftMappings, null, null, null,
                                            geometryParams );
    }

    private void process( FeatureTypeJAXB ftDecl )
                            throws SQLException, FeatureStoreException {

        if ( ftDecl.getTable() == null || ftDecl.getTable().isEmpty() ) {
            String msg = "Feature type element without or with empty table attribute.";
            throw new FeatureStoreException( msg );
        }

        QTableName table = new QTableName( ftDecl.getTable() );
        LOG.debug( "Processing feature type mapping for table '" + table + "'." );
        if ( getColumns( table ).isEmpty() ) {
            throw new FeatureStoreException( "No table with name '" + table + "' exists (or no columns defined)." );
        }

        QName ftName = ftDecl.getName();
        if ( ftName == null ) {
            LOG.debug( "Using table name for feature type." );
            ftName = new QName( table.getTable() );
        }
        ftName = makeFullyQualified( ftName, "app", "http://www.deegree.org/app" );
        LOG.debug( "Feature type name: '" + ftName + "'." );

        FIDMapping fidMapping = buildFIDMapping( table, ftName, ftDecl.getFIDMapping() );

        List<JAXBElement<? extends AbstractPropertyJAXB>> propDecls = ftDecl.getAbstractProperty();
        if ( propDecls != null && !propDecls.isEmpty() ) {
            process( table, ftName, fidMapping, propDecls );
        } else {
            process( table, ftName, fidMapping );
        }
    }

    private void process( QTableName table, QName ftName, FIDMapping fidMapping )
                            throws SQLException {

        LOG.debug( "Deriving properties and mapping for feature type '" + ftName + "' from table '" + table + "'" );

        List<PropertyType> pts = new ArrayList<PropertyType>();
        List<Mapping> mappings = new ArrayList<Mapping>();

        Set<String> fidColumnNames = new HashSet<String>();
        for ( Pair<String, BaseType> column : fidMapping.getColumns() ) {
            fidColumnNames.add( column.first.toLowerCase() );
        }

        for ( ColumnMetadata md : getColumns( table ).values() ) {
            if ( fidColumnNames.contains( md.column.toLowerCase() ) ) {
                LOG.debug( "Omitting column '" + md.column + "' from properties. Used in FIDMapping." );
                continue;
            }

            DBField dbField = new DBField( md.column );
            QName ptName = makeFullyQualified( new QName( md.column ), ftName.getPrefix(), ftName.getNamespaceURI() );
            if ( md.geomType == null ) {
                try {
                    BaseType type = BaseType.valueOf( md.sqlType );
                    PropertyType pt = new SimplePropertyType( ptName, 0, 1, type, null, null );
                    pts.add( pt );
                    PropertyName path = new PropertyName( ptName );
                    PrimitiveType primType = new PrimitiveType( type );
                    PrimitiveMapping mapping = new PrimitiveMapping( path, true, dbField, primType, null, null );
                    mappings.add( mapping );
                } catch ( IllegalArgumentException e ) {
                    LOG.warn( "Skipping column with type code '" + md.sqlType + "' from list of properties:"
                              + e.getMessage() );
                }
            } else {
                PropertyType pt = new GeometryPropertyType( ptName, 0, 1, null, null, md.geomType,
                                                            md.geometryParams.getDim(), INLINE );
                pts.add( pt );
                PropertyName path = new PropertyName( ptName );
                GeometryMapping mapping = new GeometryMapping( path, true, dbField, md.geomType, md.geometryParams,
                                                               null );
                mappings.add( mapping );
            }
        }

        FeatureType ft = new GenericFeatureType( ftName, pts, false );
        ftNameToFt.put( ftName, ft );

        FeatureTypeMapping ftMapping = new FeatureTypeMapping( ftName, table, fidMapping, mappings );
        ftNameToMapping.put( ftName, ftMapping );
    }

    private void process( QTableName table, QName ftName, FIDMapping fidMapping,
                          List<JAXBElement<? extends AbstractPropertyJAXB>> propDecls )
                            throws FeatureStoreException, SQLException {

        List<PropertyType> pts = new ArrayList<PropertyType>();
        List<Mapping> mappings = new ArrayList<Mapping>();

        for ( JAXBElement<? extends AbstractPropertyJAXB> propDeclEl : propDecls ) {
            AbstractPropertyJAXB propDecl = propDeclEl.getValue();
            Pair<PropertyType, Mapping> pt = process( table, propDecl );
            pts.add( pt.first );
            mappings.add( pt.second );
        }

        FeatureType ft = new GenericFeatureType( ftName, pts, false );
        ftNameToFt.put( ftName, ft );

        FeatureTypeMapping ftMapping = new FeatureTypeMapping( ftName, table, fidMapping, mappings );
        ftNameToMapping.put( ftName, ftMapping );
    }

    private Pair<PropertyType, Mapping> process( QTableName table, AbstractPropertyJAXB propDecl )
                            throws FeatureStoreException, SQLException {

        PropertyType pt = null;
        QName propName = propDecl.getName();
        if ( propName != null ) {
            propName = makeFullyQualified( propName, "app", "http://www.deegree.org/app" );
        }

        MappingExpression mapping = parseMappingExpression( propDecl.getMapping() );
        if ( !( mapping instanceof DBField ) ) {
            throw new FeatureStoreException( "Unhandled mapping type '" + mapping.getClass()
                                             + "'. Currently, only DBFields are supported." );
        }
        String columnName = ( (DBField) mapping ).getColumn();
        if ( propName == null ) {
            LOG.debug( "Using column name for property name." );
            propName = new QName( columnName );
            propName = makeFullyQualified( propName, "app", "http://www.deegree.org/app" );
        }

        Join joinConfig = propDecl.getJoin();
        List<TableJoin> jc = null;
        QTableName valueTable = table;
        if ( joinConfig != null ) {
            jc = buildJoinTable( table, joinConfig );
            DBField dbField = new DBField( jc.get( 0 ).getToTable().getTable(), jc.get( 0 ).getToColumns().get( 0 ) );
            valueTable = new QTableName( dbField.getTable(), dbField.getSchema() );
        }
        int maxOccurs = joinConfig != null ? -1 : 1;

        PropertyName path = new PropertyName( propName );
        ColumnMetadata md = getColumn( valueTable, columnName.toLowerCase() );
        int minOccurs = joinConfig != null ? 0 : md.isNullable ? 0 : 1;

        Mapping m = null;
        if ( propDecl instanceof SimplePropertyJAXB ) {
            SimplePropertyJAXB simpleDecl = (SimplePropertyJAXB) propDecl;
            BaseType primType = null;
            if ( simpleDecl.getType() != null ) {
                primType = getPrimitiveType( simpleDecl.getType() );
            } else {
                primType = valueOf( md.sqlType );
            }
            pt = new SimplePropertyType( propName, minOccurs, maxOccurs, primType, null, null );
            m = new PrimitiveMapping( path, minOccurs == 0, mapping, ( (SimplePropertyType) pt ).getPrimitiveType(),
                                      jc, null );
        } else if ( propDecl instanceof GeometryPropertyJAXB ) {
            GeometryPropertyJAXB geomDecl = (GeometryPropertyJAXB) propDecl;
            GeometryType type = null;
            if ( geomDecl.getType() != null ) {
                type = GeometryType.fromGMLTypeName( geomDecl.getType().name() );
            } else {
                type = md.geomType;
            }
            ICRS crs = null;
            if ( geomDecl.getCrs() != null ) {
                crs = CRSManager.getCRSRef( geomDecl.getCrs() );
            } else {
                crs = md.geometryParams.getCrs();
            }
            String srid = null;
            if ( geomDecl.getSrid() != null ) {
                srid = geomDecl.getSrid().toString();
            } else {
                srid = md.geometryParams.getSrid();
            }
            CoordinateDimension dim = null;
            if ( geomDecl.getDim() != null ) {
                // TODO why does JAXB return a list here?
                dim = DIM_2;
            } else {
                dim = md.geometryParams.getDim();
            }
            pt = new GeometryPropertyType( propName, minOccurs, maxOccurs, null, null, type, dim, INLINE );
            m = new GeometryMapping( path, minOccurs == 0, mapping, type, new GeometryStorageParams( crs, srid, dim ),
                                     jc );
        } else {
            LOG.warn( "Unhandled property declaration '" + propDecl.getClass() + "'. Skipping it." );
        }
        return new Pair<PropertyType, Mapping>( pt, m );
    }

    private FIDMapping buildFIDMapping( QTableName table, QName ftName, FIDMappingJAXB config )
                            throws FeatureStoreException, SQLException {

        String prefix = config != null ? config.getPrefix() : null;
        if ( prefix == null ) {
            prefix = ftName.getPrefix().toUpperCase() + "_" + ftName.getLocalPart().toUpperCase() + "_";
        }

        // build FID columns / types from configuration
        List<Pair<String, BaseType>> columns = new ArrayList<Pair<String, BaseType>>();
        if ( config != null && config.getColumn() != null ) {
            for ( ColumnJAXB configColumn : config.getColumn() ) {
                String columnName = configColumn.getName();
                BaseType columnType = configColumn.getType() != null ? getPrimitiveType( configColumn.getType() )
                                                                    : null;
                if ( columnType == null ) {
                    ColumnMetadata md = getColumn( table, columnName.toLowerCase() );
                    columnType = BaseType.valueOf( md.sqlType );
                }
                columns.add( new Pair<String, BaseType>( columnName, columnType ) );
            }
        }

        IDGenerator generator = buildGenerator( config );
        if ( generator instanceof AutoIDGenerator ) {
            if ( columns.isEmpty() ) {
                // determine autoincrement column automatically
                for ( ColumnMetadata md : getColumns( table ).values() ) {
                    if ( md.isAutoincrement ) {
                        BaseType columnType = BaseType.valueOf( md.sqlType );
                        columns.add( new Pair<String, BaseType>( md.column, columnType ) );
                        break;
                    }
                }
                if ( columns.isEmpty() ) {
                    throw new FeatureStoreException( "No autoincrement column in table '" + table
                                                     + "' found. Please specify column in FIDMapping manually." );
                }
            }
        } else {
            if ( columns.isEmpty() ) {
                throw new FeatureStoreException( "No FIDMapping columns for table '" + table
                                                 + "' specified. This is only possible for AutoIDGenerator." );
            }
        }

        return new FIDMapping( prefix, "_", columns, generator );
    }

    private QName makeFullyQualified( QName qName, String defaultPrefix, String defaultNamespace ) {
        String prefix = qName.getPrefix();
        String namespace = qName.getNamespaceURI();
        String localPart = qName.getLocalPart();
        if ( DEFAULT_NS_PREFIX.equals( prefix ) ) {
            prefix = defaultPrefix;
            namespace = defaultNamespace;
        }
        if ( NULL_NS_URI.equals( namespace ) ) {
            namespace = defaultNamespace;
        }
        return new QName( namespace, localPart, prefix );
    }

    private DatabaseMetaData getDBMetadata()
                            throws SQLException {
        if ( md == null ) {
            md = conn.getMetaData();
        }
        return md;
    }

    private ColumnMetadata getColumn( QTableName qTable, String columnName )
                            throws SQLException, FeatureStoreException {
        ColumnMetadata md = getColumns( qTable ).get( columnName.toLowerCase() );
        if ( md == null ) {
            throw new FeatureStoreException( "Table '" + qTable + "' does not have a column with name '" + columnName
                                             + "'" );
        }
        return md;
    }

    private LinkedHashMap<String, ColumnMetadata> getColumns( QTableName qTable )
                            throws SQLException {

        LinkedHashMap<String, ColumnMetadata> columnNameToMD = tableNameToColumns.get( qTable.toString().toLowerCase() );

        if ( columnNameToMD == null ) {
            DatabaseMetaData md = getDBMetadata();
            columnNameToMD = new LinkedHashMap<String, ColumnMetadata>();
            ResultSet rs = null;
            try {
                LOG.debug( "Analyzing metadata for table {}", qTable );
                String dbSchema = qTable.getSchema() != null ? qTable.getSchema() : dialect.getDefaultSchema();
                String table = qTable.getTable();
                rs = md.getColumns( null, dbSchema, table.toLowerCase(), "%" );
                while ( rs.next() ) {
                    String column = rs.getString( 4 );
                    int sqlType = rs.getInt( 5 );
                    String sqlTypeName = rs.getString( 6 );
                    boolean isNullable = "YES".equals( rs.getString( 18 ) );
                    boolean isAutoincrement = "YES".equals( rs.getString( 23 ) );
                    LOG.debug( "Found column '" + column + "', typeName: '" + sqlTypeName + "', typeCode: '" + sqlType
                               + "', isNullable: '" + isNullable + "', isAutoincrement:' " + isAutoincrement + "'" );

                    if ( sqlTypeName.equals( "geometry" ) ) {
                        String srid = "-1";
                        ICRS crs = CRSManager.getCRSRef( "EPSG:4326", true );
                        CoordinateDimension dim = DIM_2;
                        GeometryPropertyType.GeometryType geomType = GeometryType.GEOMETRY;
                        Statement stmt = null;
                        ResultSet rs2 = null;
                        try {
                            stmt = conn.createStatement();
                            String sql = dialect.geometryMetadata( dbSchema, table, column );
                            rs2 = stmt.executeQuery( sql );
                            rs2.next();
                            if ( rs2.getInt( 2 ) != -1 ) {
                                crs = CRSManager.lookup( "EPSG:" + rs2.getInt( 2 ), true );
                            }
                            if ( rs2.getInt( 1 ) == 3 ) {
                                dim = DIM_3;
                            }
                            srid = "" + rs2.getInt( 2 );
                            geomType = getGeometryType( rs2.getString( 3 ) );
                            LOG.debug( "Derived geometry type: " + geomType + ", crs: " + crs + ", srid: " + srid
                                       + ", dim: " + dim + "" );
                        } catch ( Exception e ) {
                            LOG.warn( "Unable to determine geometry column details: " + e.getMessage()
                                      + ". Using defaults." );
                        } finally {
                            JDBCUtils.close( rs2, stmt, null, LOG );
                        }
                        ColumnMetadata columnMd = new ColumnMetadata( column, sqlType, sqlTypeName, isNullable,
                                                                      geomType, dim, crs, srid );
                        columnNameToMD.put( column.toLowerCase(), columnMd );
                    } else {
                        ColumnMetadata columnMd = new ColumnMetadata( column, sqlType, sqlTypeName, isNullable,
                                                                      isAutoincrement );
                        columnNameToMD.put( column.toLowerCase(), columnMd );
                    }
                }
                tableNameToColumns.put( qTable.toString().toLowerCase(), columnNameToMD );
            } finally {
                JDBCUtils.close( rs );
            }
        }
        return columnNameToMD;
    }
}

class ColumnMetadata {

    String column;

    int sqlType;

    String sqlTypeName;

    boolean isNullable;

    boolean isAutoincrement;

    GeometryType geomType;

    GeometryStorageParams geometryParams;

    ColumnMetadata( String column, int sqlType, String sqlTypeName, boolean isNullable, boolean isAutoincrement ) {
        this.column = column;
        this.sqlType = sqlType;
        this.sqlTypeName = sqlTypeName;
        this.isNullable = isNullable;
        this.isAutoincrement = isAutoincrement;
    }

    public ColumnMetadata( String column, int sqlType, String sqlTypeName, boolean isNullable, GeometryType geomType,
                           CoordinateDimension dim, ICRS crs, String srid ) {
        this.column = column;
        this.sqlType = sqlType;
        this.sqlTypeName = sqlTypeName;
        this.isNullable = isNullable;
        this.geomType = geomType;
        this.geometryParams = new GeometryStorageParams( crs, srid, dim );
    }
}