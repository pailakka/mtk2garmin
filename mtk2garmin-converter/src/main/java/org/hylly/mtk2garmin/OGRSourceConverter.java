package org.hylly.mtk2garmin;

import com.typesafe.config.Config;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.gdal.gdal.gdal;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Feature;
import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.mapdb.HTreeMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class OGRSourceConverter {
    private final Logger logger = Logger.getLogger(MTKToGarminConverter.class.getName());
    private final String inputKey;
    private final String inputPath;
    private final StringTable stringTable;
    private final int TYYPPI_STRING_ID;
    private final TagHandler tagHandler;
    private final Path outFile;
    private final List<Double> bboxFilter;
    private final List<String> inputLayers;
    private final String attributeFilter;


    private final GeomUtils geomUtils;
    private final FeatureIDProvider featureIDProvider;
    private final Optional<HashSet<String>> wantedFields;
    private final HTreeMap<Long, Long> nodeIDs;
    private final NodeIDMapBuilder nodeIDMapBuilder;
    private final boolean recursivePath;
    private final String layerMatch;
    private CoordinateTransformation srctowgs;
    private final MultiOSMPBFWriter multiOSMPBFWriter;

    public OGRSourceConverter(HTreeMap<Long, Long> nodeIDs, Path outDir, List<Double> bboxFilter, String inputKey, Config inputConfig) {
        logger.info("Starting converter for " + inputKey);
        this.inputKey = inputKey;
        this.outFile = outDir.resolve(String.format("%s.osm.pbf", inputKey));
        this.inputPath = inputConfig.getString("path");
        this.inputLayers = inputConfig.getStringList("layers");
        this.layerMatch = inputConfig.hasPath("layerMatch") ? inputConfig.getString("layerMatch") : null;
        this.wantedFields = inputConfig.hasPath("wantedFields") ? Optional.of(new HashSet<>(inputConfig.getStringList("wantedFields"))) : Optional.empty();
        this.attributeFilter = inputConfig.getString("attributeFilter");
        this.recursivePath = inputConfig.hasPath("recursivePath") && inputConfig.getBoolean("recursivePath");
        this.bboxFilter = bboxFilter;

        this.stringTable = new StringTable();
        this.TYYPPI_STRING_ID = this.stringTable.getStringId("tyyppi");

        this.tagHandler = resolveTagHandler(this.inputKey);

        this.geomUtils = new GeomUtils();
        this.featureIDProvider = new FeatureIDProvider();

        this.multiOSMPBFWriter = new MultiOSMPBFWriter(outDir, inputKey);

        this.nodeIDMapBuilder = new NodeIDMapBuilder();
        this.nodeIDs = Objects.requireNonNullElseGet(nodeIDs, () -> this.nodeIDMapBuilder.getNodeIDMap(inputKey));
    }

    private TagHandler resolveTagHandler(String inputKey) {
        switch (inputKey) {
            case "syvyyskayrat":
            case "syvyyspisteet":
                return new ShapeSyvyysTagHandler(this.stringTable);
            case "mtkkorkeus":
            case "mtkmaasto":
            case "krk":
                return new MTKTagHandler(this.stringTable);
            default:
                return new GenericTagHandler(this.stringTable);
        }
    }

    public void convert() {
        logger.info("Starting conversion for \"" + this.inputKey + "\" from " + this.inputPath + " to " + this.outFile + " (resursive: " + this.recursivePath + ")");
        if (this.recursivePath) {
            try {
                Files.walk(Paths.get(this.inputPath))
                        .filter(Files::isRegularFile)
                        .parallel()
                        .forEach(fn -> {
                            String inp = fn.getFileName().toString().toLowerCase().endsWith(".zip") ? "/vsizip/" + fn : fn.toString();
                            DataSource ds = startReadingOGRFile(inp);
                            convertOGResource(ds);
                            ds.delete();
                        });
            } catch (IOException e) {
                e.printStackTrace();
                logger.severe("Failed to traverse recursive path");
            }
        } else {
            DataSource ds = startReadingOGRFile(this.inputPath);
            convertOGResource(ds);
            ds.delete();
        }

        try {
            multiOSMPBFWriter.closeMultiWriter();
            multiOSMPBFWriter.writeMergedFile();
        } catch (
                IOException e) {
            e.printStackTrace();
            logger.severe("Failed writing merged OSMPBF!");
        }
        this.nodeIDMapBuilder.closeNodeIDMap();
    }

    private DataSource startReadingOGRFile(String fn) {
        logger.info("Initializing file " + fn);
        gdal.SetConfigOption("OGR_SQLITE_CACHE", "1024MB");
        DataSource ds = ogr.Open(fn, false);
        if (ds == null) {
            logger.severe("Reading file " + fn + " failed");
            System.exit(1);

        }
        return ds;
    }

    private void convertOGResource(DataSource ds) {
        logger.info("Start converting " + ds.GetName());
        logger.info("Attribute filter: " + this.attributeFilter);
        logger.info("BBOX filter: " + this.bboxFilter);

        String attrFilter = this.attributeFilter;
        AtomicReference<Boolean> breakLayerLoop = new AtomicReference<>(false);

        AtomicLong numFeatures = new AtomicLong(0);
        long st = System.currentTimeMillis();
        AtomicLong stt = new AtomicLong(st);
        AtomicLong numNodes = new AtomicLong(0);

        List<String> wantedLayers = this.inputLayers;
        String finalLayerMatch = layerMatch;
        if (this.inputLayers.isEmpty() && this.layerMatch != null) {
            wantedLayers = IntStream.range(0, ds.GetLayerCount())
                    .mapToObj(ds::GetLayerByIndex)
                    .map(Layer::GetName)
                    .filter(n -> n.contains(finalLayerMatch))
                    .collect(Collectors.toList());
        }
        logger.info("wanted layers: " + wantedLayers);
        wantedLayers.stream()
                .takeWhile($ -> !breakLayerLoop.get())
                .forEach(layerName -> {
                    Layer lyr = ds.GetLayer(layerName);

                    Vector<String> ignoredFields = new Vector<>();

                    if (this.bboxFilter.size() == 4) {
                        lyr.SetSpatialFilterRect(this.bboxFilter.get(0), this.bboxFilter.get(1), this.bboxFilter.get(2), this.bboxFilter.get(3));
                    }

                    if (attrFilter != null) {
                        lyr.SetAttributeFilter(attrFilter);
                    }

                    logger.info("Converting layer " + layerName);

                    FeatureDefn lyrdefn = lyr.GetLayerDefn();
                    ArrayList<Field> fieldMapping = new ArrayList<>();
                    for (int i1 = 0; i1 < lyrdefn.GetFieldCount(); i1++) {
                        FieldDefn fdefn = lyrdefn.GetFieldDefn(i1);
                        String fname = fdefn.GetName();

                        if (this.wantedFields.isPresent()) {
                            if (!this.wantedFields.get().contains(fname)) {
                                ignoredFields.add(fname);
                                continue;
                            }
                        }
                        fieldMapping.add(new Field(fname, fdefn.GetFieldType(), i1));
                    }

                    if (lyr.TestCapability(ogr.OLCIgnoreFields) && ignoredFields.size() > 0) {
                        lyr.SetIgnoredFields(ignoredFields);
                    }

                    lyr.ResetReading();


                    Supplier<Feature> layerFeatureStream = lyr::GetNextFeature;

                    Stream<Pair<Geometry, Map<String, String>>> elementPairStream = Stream.generate(layerFeatureStream)
                            .takeWhile(feat -> feat != null && !breakLayerLoop.get())
                            .filter(feat -> feat.GetGeometryRef() != null)
                            .map(feat -> {
                                Geometry geom = feat.GetGeometryRef().Clone();
                                HashMap<String, String> fields = new HashMap<>();
                                for (Field f : fieldMapping) {
                                    String fname = f.getFieldName().intern();
                                    String fvalue = feat.GetFieldAsString(f.getFieldIndex()).intern();
                                    fields.put(fname, fvalue);
                                }

                                Pair<Geometry, Map<String, String>> ret = Pair.of(geom, fields);
                                feat.delete();
                                return ret;
                            });

                    Stream<Triple<List<Node>, List<Way>, List<Relation>>> elementStream = BatchSpliterator.batch(elementPairStream, 100)
                            .parallel()
                            .flatMap(batchElementPairs -> batchElementPairs.stream().map(elementPair -> {
                                Optional<Triple<List<Node>, List<Way>, List<Relation>>> handlerResult = this.handleFeature(lyr.GetName(), elementPair.getLeft(), elementPair.getRight());
                                if (handlerResult.isEmpty()) {
                                    logger.severe("BREAK");
                                    breakLayerLoop.set(true);
                                }


                                handlerResult.ifPresent(elems -> numNodes.addAndGet(elems.getLeft().size()));

                                long n = numFeatures.incrementAndGet();
                                if (n % 10000 == 0) {
                                    long msFromSttart = System.currentTimeMillis() - st;
                                    long msFromPrev = System.currentTimeMillis() - stt.get();
                                    logger.info(numNodes.get() + " nodes and " + n + " features processed in " + msFromSttart + "ms and " + msFromPrev + "ms/10000");
                                    stt.set(System.currentTimeMillis());
                                    nodeIDs.expireEvict();
                                }
                                return handlerResult;
                            }))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .filter(elems -> !elems.getLeft().isEmpty() || !elems.getMiddle().isEmpty() || !elems.getRight().isEmpty());


                    BatchSpliterator.batch(elementStream, 50000)
                            .forEach(batchElems -> {
                                List<Node> nodes = new ArrayList<>();
                                List<Way> ways = new ArrayList<>();
                                List<Relation> relations = new ArrayList<>();
                                batchElems.forEach(elems -> {
                                    nodes.addAll(elems.getLeft());
                                    ways.addAll(elems.getMiddle());
                                    relations.addAll(elems.getRight());
                                });
                                logger.info("Elements to write: " + nodes.size() + " n, " + ways.size() + " w, " + relations.size() + " r");
                                try {
                                    multiOSMPBFWriter.writeNodes(this.stringTable, nodes.stream());
                                    multiOSMPBFWriter.writeWays(this.stringTable, ways.stream());
                                    multiOSMPBFWriter.writeRelations(this.stringTable, relations.stream());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    logger.severe("Failed writing OSMPBF for elements!");
                                    breakLayerLoop.set(true);
                                }
                            });
                });
    }

    private Optional<Triple<List<Node>, List<Way>, List<Relation>>> handleFeature(String lyrname, Geometry geom, Map<String, String> rawFields) {
        List<Node> nodes = new ArrayList<>();
        List<Way> ways = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();

        Int2ObjectArrayMap<String> fields = new Int2ObjectArrayMap<>();
        for (Map.Entry<String, String> fe : rawFields.entrySet()) {
            int fid = this.stringTable.getStringId(fe.getKey());
            fields.put(fid, fe.getValue());
        }

        geom = geom.SimplifyPreserveTopology(0.5);

        if (srctowgs == null) {
            SpatialReference sref = geom.GetSpatialReference();
            srctowgs = this.geomUtils.getTransformationToWGS84(sref.ExportToProj4());
        }

        GeomHandlerResult ghr;

        if (geom == null) {
            System.out.println("empty geom 2");
            return Optional.of(Triple.of(nodes, ways, relations));
        }

        if (geom.GetGeometryCount() < 2) {
            if (geom.GetGeometryCount() > 0) {
                geom = geom.GetGeometryRef(0);
            }
            ghr = this.handleSingleGeom(geom);
        } else {
            ghr = this.handleMultiGeom(this.stringTable.getStringId("type"), this.stringTable.getStringId("multipolygon"), geom);
        }
        double geomarea = geom.Area();

        geom.delete();

        String tyyppi = lyrname.toLowerCase();
        if (tyyppi.endsWith("kiinteistoraja")) tyyppi = "kiinteistoraja";

        int tyyppi_value_id = this.stringTable.getStringId(tyyppi);

        for (Node n : ghr.nodes) {
            if (!n.isWaypart()) {
                n.addTag(this.TYYPPI_STRING_ID, tyyppi_value_id);
                tagHandler.addElementTags(n.nodeTags, fields, tyyppi, geomarea);
            }
            nodes.add(n);
        }

        for (Way w : ghr.ways) {
            if (!w.getRole().equals("inner")) {
                w.tags.put(this.TYYPPI_STRING_ID, tyyppi_value_id);
                tagHandler.addElementTags(w.tags, fields, tyyppi, geomarea);
            }
            ways.add(w);
        }

        for (Relation r : ghr.relations) {

            r.tags.put(this.TYYPPI_STRING_ID, tyyppi_value_id);
            tagHandler.addElementTags(r.tags, fields, tyyppi, geomarea);
            relations.add(r);
        }

        return Optional.of(Triple.of(nodes, ways, relations));

    }

    private GeomHandlerResult handleSingleGeom(Geometry geom) {

        GeomHandlerResult ghr = new GeomHandlerResult();

        if (geom.IsEmpty()) {
            return ghr;
        }

        boolean geomIsPoint = geom.GetGeometryType() == ogr.wkbPoint || geom.GetGeometryType() == ogr.wkbPoint25D;

        double[][] srcpoints = geom.GetPoints();
        double[][] wgspoints = geom.GetPoints();

        srctowgs.TransformPoints(wgspoints);
        Way w = null;
        long wid;
        if (!geomIsPoint) {
            wid = featureIDProvider.getWayID();
            w = new Way();
            w.id = wid;
        }

        for (int i = 0; i < srcpoints.length; i++) {

            long phash = geomUtils.hashCoords(srcpoints[i][0], srcpoints[i][1]);

            long nodeid;
            if (!nodeIDs.containsKey(phash)) {
                nodeid = featureIDProvider.getNodeID();
                Node n = new Node(nodeid, phash, wgspoints[i][0], wgspoints[i][1], !geomIsPoint);
                nodeIDs.put(phash, nodeid);
                ghr.nodes.add(n);
            } else {
                nodeid = nodeIDs.get(phash);
            }

            if (!geomIsPoint) {
                w.refs.add(nodeid);
            }
        }

        if (w != null) {
            ghr.ways.add(w);
        }

        return ghr;

    }

    private GeomHandlerResult handleMultiGeom(int type, int multipolygon, Geometry geom) {

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
