package org.hylly.mtk2garmin;

import com.typesafe.config.Config;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.gdal.ogr.DataSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

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
    private final FeatureIDProvider featureIDProvider;
    private final CachedAdditionalDataSources cachedDatasources;
    private final Config conf;
    private final Driver memoryd = ogr.GetDriverByName("memory");
    Logger logger = Logger.getLogger(CachedAdditionalDataSources.class.getName());

    private final short tyyppi_string_id;

    private final ShapeRetkeilyTagHandler retkeilyTagHandler;
    private final ShapeSyvyysTagHandler syvyysTagHandler;
    private final MMLTagHandler tagHandlerMML;
    private final StringTable stringtable;

    private final Set<String> leftLetters = new HashSet<>(
            Arrays.asList("A", "B", "C", "D"));

    private final Set<String> rightLetters = new HashSet<>(
            Arrays.asList("E", "F", "G", "H"));

    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>(50000);
    private final Long2ObjectOpenHashMap<Way> ways = new Long2ObjectOpenHashMap<>(5000);
    private final Long2ObjectOpenHashMap<Relation> relations = new Long2ObjectOpenHashMap<>(500);
    private CoordinateTransformation srctowgs;

    private double minx;
    private double miny;
    private double maxx;
    private double maxy;

    SingleCellConverter(
            File cellFile,
            Path outdir,
            Config conf, Object2ObjectRBTreeMap<String, double[]> gridExtents,
            MMLFeaturePreprocess featurePreprocessMML,
            ShapeFeaturePreprocess shapePreprocessor,
            GeomUtils geomUtils,
            FeatureIDProvider featureIDProvider, CachedAdditionalDataSources cachedDatasources) {

        this.cellFile = cellFile;
        this.outdir = outdir;
        this.conf = conf;
        this.gridExtents = gridExtents;
        this.featurePreprocessMML = featurePreprocessMML;
        this.shapePreprocessor = shapePreprocessor;
        this.geomUtils = geomUtils;
        this.featureIDProvider = featureIDProvider;
        this.cachedDatasources = cachedDatasources;

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

        DataSource mtkds = readOGRsource(stringtable, startReadingOGRFile("/vsizip/" + cellFile.toString()), featurePreprocessMML, tagHandlerMML, null);
        mtkds.delete();
        printCounts();
        System.out.println(Arrays.toString(mml_extent));

        File cellKrkPath = new File(Paths.get(conf.getString("kiinteistorajat"), cell.substring(0, 3)).toString());
        File[] krkFiles = cellKrkPath.listFiles();

        if (krkFiles != null) {
            for (File krkf : krkFiles) {
                String krkfn = krkf.getName();
                if (!krkfn.startsWith(cellWithoutLetter)) continue;
                String krkCell = krkfn.substring(krkfn.lastIndexOf(File.separator) + 1, krkfn.lastIndexOf(File.separator) + 7);
                String krkCellLetter = krkCell.substring(krkCell.length() - 1);

                if ("L".equals(cellLetter) && !leftLetters.contains(krkCellLetter)) continue;
                if ("R".equals(cellLetter) && !rightLetters.contains(krkCellLetter)) continue;


                System.out.println(krkCell + " / " + krkCellLetter);
                System.out.println(krkf.getAbsolutePath());
                DataSource krkds = readOGRsource(stringtable, startReadingOGRFile("/vsizip/" + krkf.getAbsolutePath() + "/" + krkCell + "_kiinteistoraja.shp"), shapePreprocessor, tagHandlerMML, mml_extent);
                krkds.delete();
            }
            printCounts();
            System.out.println(Arrays.toString(mml_extent));
        } else {
            System.out.println("No krk exists for " + cell);
        }

        nodes.clear();
        ways.clear();
        relations.clear();


        cachedDatasources.getDatasources()
                .forEach(cachedDatasource -> {
                    DataSource extds = readOGRsource(stringtable, cachedDatasource, shapePreprocessor, getTagHandlerForDatasource(cachedDatasource), mml_extent);
                    extds.delete();
                    printCounts();
//                    cachedDatasource.delete();
                });

        osmpbWriter.writeOSMPBFElements(stringtable, nodes, ways, relations);
        osmpbWriter.closeOSMPBFFile();
    }

    private TagHandlerI getTagHandlerForDatasource(DataSource ds) {
        switch (ds.GetLayer(0).GetName()) {
            case "syvyyskayra_v":
            case "syvyyspiste_p":
                return syvyysTagHandler;
            case "kesaretkeilyreitit":
            case "ulkoilureitit":
            case "luontopolut":
            case "point_dump":
                return retkeilyTagHandler;
            default:
                logger.severe("Unknown cached datasource ds name " + ds.GetLayer(0).GetName());
                return null;
        }
    }

    private DataSource startReadingOGRFile(String fn) {
        System.out.println("Initializing file " + fn);

        DataSource ds = ogr.Open(fn, false);
        if (ds == null) {
            System.out.println("Reading file " + fn + " failed");
            System.exit(1);

        }
        return ds;
    }


    private DataSource readOGRsource(StringTable stringtable, DataSource ods, FeaturePreprocessI featurePreprocess, TagHandlerI tagHandler,
                                     double[] filterExtent) {

        if (ods == null) {
            return null;
        }
        logger.info("Copying " + ods.getName() + " to memory");
        DataSource ds = memoryd.CopyDataSource(ods, "mem_" + ods.getName());
        logger.info("Copying " + ods.getName() + " to memory done!");

        String attributefilter = featurePreprocess.getAttributeFilterString();

        HashSet<String> ignored_fields = new HashSet<>();

        for (int i = 0; i < ds.GetLayerCount(); i++) {
            Layer lyr = ds.GetLayer(i);
            Vector<String> ignoredFields = new Vector<>();

            if (filterExtent != null) {
                logger.info("Filter rect: " + Arrays.toString(filterExtent));
                lyr.SetSpatialFilterRect(filterExtent[0], filterExtent[2], filterExtent[1], filterExtent[3]);
            }

            if (attributefilter != null) {
                logger.info("Attribute filter: " + attributefilter);
                lyr.SetAttributeFilter(attributefilter);
            }

            FeatureDefn lyrdefn = lyr.GetLayerDefn();
            ArrayList<Field> fieldMapping = new ArrayList<>();
            for (int i1 = 0; i1 < lyrdefn.GetFieldCount(); i1++) {
                FieldDefn fdefn = lyrdefn.GetFieldDefn(i1);
                String fname = fdefn.GetName();

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

            AtomicReference<Boolean> breakLayerLoop = new AtomicReference<>(false);
            Supplier<Feature> layerFeatureStream = lyr::GetNextFeature;

//            logger.info("Reading " + lyr.GetFeatureCount() + "  from " + lyr.GetName() + " on " + ds.getName());
            Stream.generate(layerFeatureStream)
                    .takeWhile(feat -> feat != null && !breakLayerLoop.get())
                    .forEach(feat -> {
                        if (feat == null) {
                            logger.severe("NULL feature encountered on layer " + lyr.GetName());
                            return;
                        }
                        if (!this.handleFeature(stringtable, lyr.GetName(), fieldMapping, feat, featurePreprocess, tagHandler)) {
                            System.out.println("BREAK");
                            breakLayerLoop.set(true);
                        }
                    });

//            logger.info(lyr.GetFeatureCount() + " features read from " + lyr.GetName() + " on " + ds.getName());
            if (breakLayerLoop.get()) {
                break;
            }
        }
        System.out.println("Ignored fields: " + Arrays.toString(ignored_fields.toArray()));
        // return is.extent;

        return ds;

    }

    private void printCounts() {
        System.out.println(nodes.size() + " nodes " + ways.size() + " ways " + relations.size() + " relations");
    }

    private boolean handleFeature(StringTable stringtable, String lyrname, ArrayList<Field> fieldMapping, Feature feat,
                                  FeaturePreprocessI featurePreprocess, TagHandlerI tagHandler) {
        Short2ObjectOpenHashMap<String> fields = new Short2ObjectOpenHashMap<>();
        Geometry geom;
        for (Field f : fieldMapping) {
            short fid = stringtable.getStringId(f.getFieldName());
            String fname = feat.GetFieldAsString(f.getFieldIndex()).intern();
            fields.put(fid, fname);
        }

        geom = feat.GetGeometryRef();

        if (geom == null) return true;


        geom = geom.SimplifyPreserveTopology(0.5);

        if (srctowgs == null) {
            SpatialReference sref = geom.GetSpatialReference();
            srctowgs = this.geomUtils.getTransformationToWGS84(sref.ExportToProj4());
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

    private GeomHandlerResult handleSingleGeom(Geometry geom) {

        GeomHandlerResult ghr = new GeomHandlerResult();

        if (geom.IsEmpty())
            return ghr;

        boolean ispoint = geom.GetGeometryType() == ogr.wkbPoint || geom.GetGeometryType() == ogr.wkbPoint25D;

        double[][] srcpoints = geom.GetPoints();
        double[][] wgspoints = geom.GetPoints();

        srctowgs.TransformPoints(wgspoints);
        Way w = null;
        long wid;
        if (!ispoint) {
            wid = featureIDProvider.getWayID();
            w = new Way();
            w.id = wid;
        }

        for (int i = 0; i < srcpoints.length; i++) {

            long phash = geomUtils.hashCoords(srcpoints[i][0], srcpoints[i][1]);
            int pcell = geomUtils.xy2grid(srcpoints[i][0], srcpoints[i][1]);

            if (!nodes.containsKey(phash)) {
                updateCoordinateMinMax(wgspoints[i]);

//                if (!nodepos.containsKey(pcell)) {
//                    System.out.println("Adding nodepos hashmap: " + pcell);
//                    nodepos.put(pcell, new Long2ObjectAVLTreeMap<>());
//                }
//
//                if (nodepos.get(pcell).containsKey(phash)) {
//                    n = nodepos.get(pcell).get(phash);
//                    n.clearTags();
//                } else {
//                    nid = nodeidcounter;
//                    nodeidcounter++;
//                    n = new Node(nid, phash, pcell, wgspoints[i][0], wgspoints[i][1], !ispoint);
//
//                    if (this.nodeNearCellBorder(srcpoints[i])) {
//                        nodepos.get(pcell).put(phash, n);
//                    }
//                }

                Node n = new Node(featureIDProvider.getNodeID(), phash, pcell, wgspoints[i][0], wgspoints[i][1], !ispoint);
                nodes.put(phash, n);
                ghr.nodes.add(n);
                if (!ispoint) {
                    w.refs.add(n.getId());
                }
            } else {
                Node n = nodes.get(phash);
                n.waypart = n.waypart || !ispoint;
                ghr.nodes.add(n);
                if (!ispoint) {
                    w.refs.add(n.getId());
                }
            }
        }

        if (w != null) {
            ghr.ways.add(w);
        }

        return ghr;

    }

    private void updateCoordinateMinMax(double[] wgspoint) {
        if (wgspoint[0] < this.minx) {
            this.minx = wgspoint[0];
        }
        if (wgspoint[1] < this.miny) {
            this.miny = wgspoint[1];
        }

        if (wgspoint[0] > this.maxx) {
            this.maxx = wgspoint[0];
        }
        if (wgspoint[1] > this.maxy) {
            this.maxy = wgspoint[1];
        }
    }

    private GeomHandlerResult handleMultiGeom(short type, short multipolygon, Geometry geom) {

        GeomHandlerResult ighr;
        Geometry igeom;
        GeomHandlerResult ghr = new GeomHandlerResult();

        if (!geom.GetGeometryName().equals("POLYGON")) {
            for (int i = 0; i < geom.GetGeometryCount(); i++) {
                igeom = geom.GetGeometryRef(i);
                ighr = this.handleSingleGeom(igeom);
                ghr.nodes.addAll(ighr.nodes);
                ghr.ways.addAll(ighr.ways);
            }
            return ghr;
        }

        long rid = featureIDProvider.getRelationID();
        Relation r = new Relation();
        r.setId(rid);
        r.tags.put(type, multipolygon);

        for (int i = 0; i < geom.GetGeometryCount(); i++) {
            igeom = geom.GetGeometryRef(i);

            ighr = this.handleSingleGeom(igeom);
            if (ighr.ways.size() == 0) {
                return new GeomHandlerResult();
            }
            ighr.ways.get(0).setRole((i == 0 ? "outer" : "inner"));

            ghr.nodes.addAll(ighr.nodes);
            ghr.ways.addAll(ighr.ways);

            RelationMember rm = new RelationMember();

            rm.setId(ighr.ways.get(0).getId());
            rm.setType();
            rm.setRole((i == 0 ? "outer" : "inner"));
            r.members.add(rm);
        }

        ghr.relations.add(r);
        return ghr;

    }

}
