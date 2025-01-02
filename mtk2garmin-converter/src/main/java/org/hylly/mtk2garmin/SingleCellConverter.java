package org.hylly.mtk2garmin;

import com.typesafe.config.Config;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class SingleCellConverter {
    private final boolean isValidCell;
    private final Logger logger = Logger.getLogger(SingleCellConverter.class.getName());

    private final File cellFile;
    private final ShapeFeaturePreprocess shapePreprocessor;
    private final MMLFeaturePreprocess featurePreprocessMML;
    private final GeomUtils geomUtils;
    private final FeatureIDProvider featureIDProvider;
    private final CachedAdditionalDataSources cachedDatasources;
    private final Config conf;
    private final Driver memoryd = ogr.GetDriverByName("memory");
    private final NodeCache nodeCache;

    private final String cell;
    private final String cellWithoutLetter;
    private final String cellLetter;

    private final double[] bbox;


    private final int tyyppi_string_id;

    private final ShapeRetkeilyTagHandler retkeilyTagHandler;
    private final ShapeSyvyysTagHandler syvyysTagHandler;
    private final MMLTagHandler tagHandlerMML;
    private final StringTable stringtable;

    private final Set<String> leftLetters = new HashSet<>(
            Arrays.asList("A", "B", "C", "D"));

    private final Set<String> rightLetters = new HashSet<>(
            Arrays.asList("E", "F", "G", "H"));

    private final Long2ObjectOpenHashMap<LightNode> nodes = new Long2ObjectOpenHashMap<>(50000);
    private final Long2ObjectOpenHashMap<LightWay> ways = new Long2ObjectOpenHashMap<>(5000);
    private final Long2ObjectOpenHashMap<LightRelation> relations = new Long2ObjectOpenHashMap<>(500);
    private final OSMPBFWriter osmpbfWriter;
    private GeomTransformer sphericToWGS;
    private GeomTransformer srcToSphericMerc;

    SingleCellConverter(
            File cellFile,
            OSMPBFWriter osmpbfWriter,
            StringTable stringtable,
            Config conf, HashMap<String, double[]> gridExtents,
            MMLFeaturePreprocess featurePreprocessMML,
            ShapeFeaturePreprocess shapePreprocessor,
            GeomUtils geomUtils,
            FeatureIDProvider featureIDProvider, CachedAdditionalDataSources cachedDatasources, NodeCache nodeCache) {

        this.cellFile = cellFile;
        this.osmpbfWriter = osmpbfWriter;
        this.conf = conf;
        this.featurePreprocessMML = featurePreprocessMML;
        this.shapePreprocessor = shapePreprocessor;
        this.geomUtils = geomUtils;
        this.featureIDProvider = featureIDProvider;
        this.cachedDatasources = cachedDatasources;
        this.nodeCache = nodeCache;


        this.stringtable = stringtable;
        this.tyyppi_string_id = stringtable.getStringId("tyyppi");
        this.tagHandlerMML = new MMLTagHandler(stringtable);
        this.retkeilyTagHandler = new ShapeRetkeilyTagHandler(stringtable);
        this.syvyysTagHandler = new ShapeSyvyysTagHandler(stringtable);

        String cellFileName = cellFile.getName();
        cell = cellFileName.substring(cellFileName.lastIndexOf(File.separator) + 1, cellFileName.lastIndexOf(File.separator) + 7);
        cellWithoutLetter = cell.substring(0, cell.length() - 1);
        cellLetter = cell.substring(cell.length() - 1);

        bbox = gridExtents.get(cell);
        this.isValidCell = bbox != null;
        logger.info(cellFileName + " (" + cell + " / " + cellWithoutLetter + " / " + cellLetter + "), extent: " + Arrays.toString(bbox));

    }

    void doConvert() throws IOException {
        DataSource mtkds = readOGRsource(stringtable, startReadingOGRFile("/vsizip/" + cellFile.toString()), featurePreprocessMML, tagHandlerMML, null);
        mtkds.delete();
        printCounts();

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


                logger.info("Adding KRK for cell " + cell + " from " + krkCell + " / " + krkCellLetter + "(" + krkf.getAbsolutePath() + ")");
                DataSource krkds = readOGRsource(stringtable, startReadingOGRFile("/vsizip/" + krkf.getAbsolutePath() + "/" + krkCell + "_kiinteistoraja.shp"), shapePreprocessor, tagHandlerMML, bbox);
                krkds.delete();
            }
            printCounts();
        } else {
            logger.warning("No krk exists for " + cell);
        }

        cachedDatasources.getDatasources()
                .forEach(cachedDatasource -> {
                    DataSource extds = readOGRsource(stringtable, cachedDatasource, shapePreprocessor, getTagHandlerForDatasource(cachedDatasource), bbox);
                    extds.delete();
                    printCounts();
                });

        osmpbfWriter.writeOSMPBFElements(stringtable, nodes, ways, relations);
    }

    private TagHandlerI getTagHandlerForDatasource(DataSource ds) {
        return switch (ds.GetLayer(0).GetName()) {
            case "syvyyskayra_v", "syvyyspiste_p" -> syvyysTagHandler;
            case "kesaretkeilyreitit", "ulkoilureitit", "luontopolut", "point_dump" -> retkeilyTagHandler;
            default -> {
                logger.severe("Unknown cached datasource ds name " + ds.GetLayer(0).GetName());
                yield null;
            }
        };
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
                lyr.SetSpatialFilterRect(filterExtent[0], filterExtent[2], filterExtent[1], filterExtent[3]);
            }

            if (attributefilter != null) {
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
            if (lyr.TestCapability(ogr.OLCIgnoreFields) && !ignoredFields.isEmpty()) {
                lyr.SetIgnoredFields(ignoredFields);
            }

            lyr.ResetReading();

            AtomicReference<Boolean> breakLayerLoop = new AtomicReference<>(false);
            Supplier<Feature> layerFeatureStream = lyr::GetNextFeature;

            Stream.generate(layerFeatureStream)
                    .takeWhile(feat -> feat != null && !breakLayerLoop.get())
                    .forEach(feat -> {
                        if (feat == null) {
                            logger.severe("NULL feature encountered on layer " + lyr.GetName());
                            return;
                        }
                        if (!this.handleFeature(stringtable, lyr.GetName(), fieldMapping, feat, tagHandler)) {
                            System.out.println("BREAK");
                            breakLayerLoop.set(true);
                        }
                    });
            if (breakLayerLoop.get()) {
                break;
            }
        }
        System.out.println("Ignored fields: " + Arrays.toString(ignored_fields.toArray()));

        return ds;

    }

    private void printCounts() {
        System.out.println(nodes.size() + " nodes " + ways.size() + " ways " + relations.size() + " relations");
    }

    private boolean handleFeature(StringTable stringtable, String lyrname, ArrayList<Field> fieldMapping, Feature feat,
                                  TagHandlerI tagHandler) {
        Int2ObjectOpenHashMap<String> fields = new Int2ObjectOpenHashMap<>();
        Geometry geom;
        for (Field f : fieldMapping) {
            int fid = stringtable.getStringId(f.getFieldName());
            String fname = feat.GetFieldAsString(f.getFieldIndex()).intern();
            fields.put(fid, fname);
        }

        geom = feat.GetGeometryRef();

        if (geom == null) return true;

        if (sphericToWGS == null || srcToSphericMerc == null) {
            SpatialReference sref = geom.GetSpatialReference();
            sphericToWGS = this.geomUtils.spherictowgs;
            srcToSphericMerc = this.geomUtils.getTransformationToSphereMercator(sref.ExportToProj4());
        }

        geom = geom.Transform(srcToSphericMerc);
        geom = geom.SimplifyPreserveTopology(0.5);

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

        int tyyppi_value_id = stringtable.getStringId(tyyppi);

        for (LightNode n : ghr.lightNodes) {

            if (!n.isWayPart()) {
                n.addTag(tyyppi_string_id, tyyppi_value_id);
                tagHandler.addElementTags(n.nodeTags, fields, tyyppi, geomarea);
            }

            if (!nodes.containsKey(n.getHash())) {
                nodes.put(n.getHash(), n);
            }
        }

        for (LightWay w : ghr.lightWays) {
            if (!w.getRole().equals("inner")) {
                w.tags.put(tyyppi_string_id, tyyppi_value_id);
                tagHandler.addElementTags(w.tags, fields, tyyppi, geomarea);
            }
            if (!ways.containsKey(w.getId())) {
                ways.put(w.getId(), w);
            }
        }

        for (LightRelation r : ghr.lightRelations) {

            r.tags.put(tyyppi_string_id, tyyppi_value_id);
            tagHandler.addElementTags(r.tags, fields, tyyppi, geomarea);
            if (!relations.containsKey(r.getId()))
                relations.put(r.getId(), r);
        }

        return true;

    }

    private GeomHandlerResult handleSingleGeom(Geometry geom) {

        GeomHandlerResult ghr = new GeomHandlerResult();

        Geometry wgsgeom = geom.Transform(sphericToWGS);
        if (geom.IsEmpty() || wgsgeom.IsEmpty()) {
            return ghr;
        }

        boolean isPoint = geom.GetGeometryType() == ogr.wkbPoint || geom.GetGeometryType() == ogr.wkbPoint25D;

        LightWay w = null;
        long wid;
        if (!isPoint) {
            wid = featureIDProvider.getWayID();
            w = new LightWay(wid);
        }

        for (int i = 0; i < geom.GetPointCount(); i++) {

            long phash = geomUtils.hashCoords(geom.GetX(i), geom.GetY(i));
            int pcell = geomUtils.xy2grid(geom.GetX(i), geom.GetY(i));

            if (!nodes.containsKey(phash)) {
                nodeCache.ensureGrid(pcell);

                Optional<Long> cachedNodeId = nodeCache.getNodeId(pcell,phash);

                long nodeID;
                if (cachedNodeId.isPresent()) {
                    nodeID = cachedNodeId.get();
                } else {
                    nodeID = featureIDProvider.getNodeID();
                    if (this.nodeNearCellBorder(geom.GetPoint(i))) {
                        nodeCache.addNodeId(pcell, phash, nodeID);
                    }
                }

                LightNode n = new LightNode(nodeID, phash, wgsgeom.GetX(i), wgsgeom.GetY(i), !isPoint);
                nodes.put(phash, n);
                ghr.lightNodes.add(n);
            } else {
                LightNode n = nodes.get(phash);
                n.wayPart = n.wayPart || !isPoint;
                ghr.lightNodes.add(n);
            }
            if (!isPoint) {
                w.refs.add(phash);
            }
        }

        if (w != null) {
            ghr.lightWays.add(w);
        }

        return ghr;

    }


    private boolean nodeNearCellBorder(double[] srcpoints) {
        double dist = this.calculateMinNodeCellBorderDistance(srcpoints[0], srcpoints[1]);
        return Math.abs(dist) < 2;
    }

    private double calculateMinNodeCellBorderDistance(double x, double y) {
        return Math.min(Math.abs(this.bbox[0] - x),
                Math.min(Math.abs(this.bbox[2] - y), Math.min(Math.abs(this.bbox[1] - x), Math.abs(this.bbox[3] - y))));
    }

    private GeomHandlerResult handleMultiGeom(int type, int multipolygon, Geometry geom) {

        GeomHandlerResult ighr;
        Geometry igeom;
        GeomHandlerResult ghr = new GeomHandlerResult();

        if (!geom.GetGeometryName().equals("POLYGON")) {
            for (int i = 0; i < geom.GetGeometryCount(); i++) {
                igeom = geom.GetGeometryRef(i);
                ighr = this.handleSingleGeom(igeom);
                ghr.lightNodes.addAll(ighr.lightNodes);
                ghr.lightWays.addAll(ighr.lightWays);
            }
            return ghr;
        }

        long rid = featureIDProvider.getRelationID();
        LightRelation r = new LightRelation();
        r.setId(rid);
        r.tags.put(type, multipolygon);

        for (int i = 0; i < geom.GetGeometryCount(); i++) {
            igeom = geom.GetGeometryRef(i);

            ighr = this.handleSingleGeom(igeom);
            if (ighr.lightWays.isEmpty()) {
                return new GeomHandlerResult();
            }
            String role = i == 0 ? "outer" : "inner";
            ighr.lightWays.getFirst().setRole(role);

            ghr.lightNodes.addAll(ighr.lightNodes);
            ghr.lightWays.addAll(ighr.lightWays);

            LightRelationMember rm = new LightRelationMember(ighr.lightWays.getFirst().getId(), role);
            r.members.add(rm);
        }

        ghr.lightRelations.add(r);
        return ghr;

    }

    public boolean isValidCell() {
        return isValidCell;
    }
}
