package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.gdal.ogr.DataSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.LongStream;

import org.gdal.ogr.*;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

public class SingleCellConverter {
    private final File cellFile;
    private final Path outdir;
    private final Object2ObjectRBTreeMap<String, double[]> gridExtents;
    private final ShapeFeaturePreprocess shapePreprocessor;
    private final MMLFeaturePreprocess featurePreprocessMML;
    private final GeomUtils geomUtils;
    private final SpatialReference wgs84ref;
    Logger logger = Logger.getLogger(CachedAdditionalDataSources.class.getName());

    private final short tyyppi_string_id;

    private final ShapeRetkeilyTagHandler retkeilyTagHandler;
    private final ShapeSyvyysTagHandler syvyysTagHandler;
    private final MMLTagHandler tagHandlerMML;
    private final StringTable stringtable;


    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>(50000);
    private final Long2ObjectOpenHashMap<Way> ways = new Long2ObjectOpenHashMap<>(5000);
    private final Long2ObjectOpenHashMap<Relation> relations = new Long2ObjectOpenHashMap<>(500);
    private Object srctowgs;

    SingleCellConverter(File cellFile, Path outdir, Object2ObjectRBTreeMap<String, double[]> gridExtents, MMLFeaturePreprocess featurePreprocessMML, ShapeFeaturePreprocess shapePreprocessor, GeomUtils geomUtils, SpatialReference wgs84ref) {

        this.cellFile = cellFile;
        this.outdir = outdir;
        this.gridExtents = gridExtents;
        this.featurePreprocessMML = featurePreprocessMML;
        this.shapePreprocessor = shapePreprocessor;
        this.geomUtils = geomUtils;
        this.wgs84ref = wgs84ref;


        this.stringtable = new StringTable();
        this.tyyppi_string_id = stringtable.getStringId("tyyppi");
        this.tagHandlerMML = new MMLTagHandler(stringtable);
        this.retkeilyTagHandler = new ShapeRetkeilyTagHandler(stringtable);
        this.syvyysTagHandler = new ShapeSyvyysTagHandler(stringtable);
    }


    void doConvert() throws IOException {
        String cellFileName = cellFile.getName();
        String cell = cellFileName.substring(cellFileName.lastIndexOf(File.separator) + 1, cellFileName.lastIndexOf(File.separator) + 7);
        String cellWithoutLetter = cell.substring(0, cell.length() - 1);
        String cellLetter = cell.substring(cell.length() - 1);

        OSMPBFWriter osmpbWriter = new OSMPBFWriter(outdir.resolve(String.format("%s.osm.pbf", cell)).toFile());


        osmpbWriter.startWritingOSMPBF();

        logger.info(cellFileName + " (" + cell + " / " + cellWithoutLetter + " / " + cellLetter + ")");

        double[] mml_extent = gridExtents.get(cell);

        DataSource mtkds = readOGRsource(stringtable, startReadingOGRFile("/vsizip/" + cellFileName), featurePreprocessMML, tagHandlerMML, true, null);
        mtkds.delete();
        printCounts();
        System.out.println(Arrays.toString(mml_extent));

    }

    private InitializedDatasource startReadingOGRFile(String fn) {
        System.out.println("Initializing file " + fn);
        InitializedDatasource is = new InitializedDatasource();
        DataSource ds = ogr.Open(fn, false);
        is.ds = ds;
        if (ds == null) {
            System.out.println("Reading file " + fn + " failed");
            System.exit(1);

        }


        is.cell = this.geomUtils.calculateDatasourceCell(is.ds);
        return is;
    }


    private DataSource readOGRsource(StringTable stringtable, InitializedDatasource is, FeaturePreprocessI featurePreprocess, TagHandlerI tagHandler,
                                     boolean doClearNodeCache, double[] filterExtent) {

        DataSource ds = is.ds;

        if (doClearNodeCache) {
            this.clearNodeCache(is.cell);
        }

        if (ds == null) {
            return is.ds;
        }
        Layer lyr;
        String fname;

        String attributefilter = featurePreprocess.getAttributeFilterString();

        HashSet<String> ignored_fields = new HashSet<>();
        FieldDefn fdefn;

        layerloop:
        for (int i = 0; i < ds.GetLayerCount(); i++) {
            lyr = ds.GetLayer(i);
            Vector<String> ignoredFields = new Vector<>();

            if (filterExtent != null) {
                System.out.println("Filter rect: " + Arrays.toString(filterExtent));
                lyr.SetSpatialFilterRect(filterExtent[0], filterExtent[2], filterExtent[1], filterExtent[3]);
            }

            if (attributefilter != null) {
                lyr.SetAttributeFilter(attributefilter);
            }

            FeatureDefn lyrdefn = lyr.GetLayerDefn();
            ArrayList<Field> fieldMapping = new ArrayList<>();
            for (int i1 = 0; i1 < lyrdefn.GetFieldCount(); i1++) {
                fdefn = lyrdefn.GetFieldDefn(i1);
                fname = fdefn.GetName();

                if (!tagHandler.getWantedFields().contains(fname)) {
                    ignoredFields.add(fname);

                } else {
                    fieldMapping.add(new Field(fname, fdefn.GetFieldType(), i1));
                }
            }


            ignored_fields.addAll(ignoredFields);
            if (lyr.TestCapability(ogr.OLCIgnoreFields) && ignoredFields.size() > 0) {
                lyr.SetIgnoredFields(ignoredFields);
            }

            lyr.ResetReading();

            Layer finalLyr = lyr;
            AtomicReference<Boolean> breakLayerLoop = new AtomicReference<>(false);
            LongStream.range(0, lyr.GetFeatureCount(1))
                    .parallel()
                    .takeWhile(fid -> breakLayerLoop.get())
                    .forEach(fid -> {
                        Feature feat = finalLyr.GetFeature(fid);
                        if (!this.handleFeature(stringtable, finalLyr.GetName(), fieldMapping, feat, featurePreprocess, tagHandler)) {
                            System.out.println("BREAK");
                            breakLayerLoop.set(true);
                        }
                    });

            if (breakLayerLoop.get()) {
                break;
            }
        }
        System.out.println("Ignored fields: " + Arrays.toString(ignored_fields.toArray()));
        // return is.extent;

        return is.ds;

    }

    private void printCounts() {
        System.out.println(nodes.size() + " nodes " + ways.size() + " ways " + relations.size() + " relations");
    }

    private boolean handleFeature(StringTable stringtable, String lyrname, ArrayList<Field> fieldMapping, Feature feat,
                                  FeaturePreprocessI featurePreprocess, TagHandlerI tagHandler) {
        Short2ObjectOpenHashMap<String> fields = new Short2ObjectOpenHashMap<>();
        Geometry geom;
        for (Field f : fieldMapping) {
            fields.put(stringtable.getStringId(f.getFieldName()),
                    feat.GetFieldAsString(f.getFieldIndex()).intern());
        }

        geom = feat.GetGeometryRef();

        if (geom == null) return true;


        geom = geom.SimplifyPreserveTopology(0.5);

        if (srctowgs == null) {
            SpatialReference sref = geom.GetSpatialReference();
            srctowgs = osr.CreateCoordinateTransformation(sref, wgs84ref);
        }

        GeomHandlerResult ghr;

        if (geom == null) return true;

        if (geom.GetGeometryCount() < 2) {
            if (geom.GetGeometryCount() > 0) {
                geom = geom.GetGeometryRef(0);
            }
            ghr = this.handleSingleGeom(geom);
        } else {
            ghr = this.handleMultiGeom(stringtable.getStringId("type"), stringtable.getStringId("multipolygon"), geom);
        }
        double geomarea = geom.Area();

        geom.delete();
        feat.delete();
        String tyyppi = lyrname.toLowerCase();
        if (tyyppi.endsWith("kiinteistoraja")) tyyppi = "kiinteistoraja";

        short tyyppi_value_id = stringtable.getStringId(tyyppi);

        for (Node n : ghr.nodes) {

            if (!n.isWaypart()) {
                n.addTag(tyyppi_string_id, tyyppi_value_id);
                tagHandler.addElementTags(n.nodeTags, fields, tyyppi, geomarea);
            }

            if (!nodes.containsKey(n.getHash())) {
                nodes.put(n.getHash(), n);
            }
        }

        for (Way w : ghr.ways) {
            if (!w.getRole().equals("inner")) {
                w.tags.put(tyyppi_string_id, tyyppi_value_id);
                tagHandler.addElementTags(w.tags, fields, tyyppi, geomarea);
            }
            if (!ways.containsKey(w.getId())) {
                ways.put(w.getId(), w);
            }
        }

        for (Relation r : ghr.relations) {

            r.tags.put(tyyppi_string_id, tyyppi_value_id);
            tagHandler.addElementTags(r.tags, fields, tyyppi, geomarea);
            if (!relations.containsKey(r.getId()))
                relations.put(r.getId(), r);
        }

        return true;

    }

}
