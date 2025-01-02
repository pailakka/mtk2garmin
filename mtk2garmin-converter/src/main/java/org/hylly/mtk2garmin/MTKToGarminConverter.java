package org.hylly.mtk2garmin;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.gdal.ogr.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;


class MTKToGarminConverter {
    private final Logger logger = Logger.getLogger(MTKToGarminConverter.class.getName());

    private final HashMap<String, double[]> gridExtents = new HashMap<>();
    private final NodeCache nodeCache;

    private final FeatureIDProvider featureIDProvider = new FeatureIDProvider();

    private final Config conf;

    private MMLFeaturePreprocess featurePreprocessMML;
    private ShapeFeaturePreprocess shapePreprocessor;
    private final GeomUtils geomUtils;
    private CachedAdditionalDataSources cachedDatasources;


    void doConvert() throws IOException {
        File mtkDirectory = new File(conf.getString("maastotietokanta"));
        if (!mtkDirectory.exists()) {
            throw new IllegalArgumentException("Maastotietokanta directory does not exists");
        }

        Stream<File> files = getMTKCellFiles(mtkDirectory);

        Map<String, List<File>> areas = files.collect(Collectors.groupingBy(
                file -> file.getName().substring(0, 4),
                Collectors.toList()
        ));


        Path outdir = Paths.get(conf.getString("output"));

        if (!Files.exists(outdir)) {
            try {
                Files.createDirectories(outdir);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        Collector<double[], double[], double[]> gridExtentCollector = Collector.of(
                () -> new double[4],
                (areabbox, cellbbox) -> {
                    areabbox[0] = areabbox[0] < 0.1 ? cellbbox[0] : Math.min(areabbox[0], cellbbox[0]);
                    areabbox[1] = areabbox[1] < 0.1 ? cellbbox[1] : Math.max(areabbox[1], cellbbox[1]);
                    areabbox[2] = areabbox[2] < 0.1 ? cellbbox[2] : Math.min(areabbox[2], cellbbox[2]);
                    areabbox[3] = areabbox[3] < 0.1 ? cellbbox[3] : Math.max(areabbox[3], cellbbox[3]);
                },
                geomUtils::extendExtent
        );

        Map<String, double[]> grid2448 = gridExtents
                .entrySet()
                .parallelStream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().substring(0, 4),
                        Collectors.mapping(Map.Entry::getValue, gridExtentCollector)

                ));

        Map<String, Optional<String>> areaRelations = getRelatedAreas(grid2448, areas);
        logger.info("Area relations resolved");

        initializeCachedDatasources();

        String areaFilter = conf.hasPath("areaFilter") ? conf.getString("areaFilter") : null;

        OSMPBFWriter osmpbWriter = new OSMPBFWriter(outdir.resolve("all_direct.osm.pbf").toFile());
        osmpbWriter.startWritingOSMPBF();
        StringTable stringTable = new StringTable();

        areas
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .filter(e -> grid2448.containsKey(e.getKey()))
                .filter(e -> areaFilter == null || e.getKey().startsWith(areaFilter))
                .filter(e -> !e.getKey().startsWith("J"))
                .forEach(areaEntry -> {
                    String areaKey = areaEntry.getKey();
                    List<File> areaCells = areaEntry.getValue();
                    double[] areaBBox = grid2448.get(areaKey);
                    int areaGrid = geomUtils.xy2grid(areaBBox[0], areaBBox[1]);
                    nodeCache.ensureGrid(areaGrid);

                    areaCells.forEach(cellFile -> {
                        logger.info("Processing file: " + cellFile.toString() + " in thread [" + Thread.currentThread().getId() + "]");
                        try {
                            SingleCellConverter cellConverter = new SingleCellConverter(cellFile, osmpbWriter, stringTable, conf, gridExtents, featurePreprocessMML, shapePreprocessor, geomUtils, featureIDProvider, cachedDatasources, nodeCache);
                            if (cellConverter.isValidCell()) {
                                cellConverter.doConvert();
                            }
                        } catch (IOException e) {
                            logger.severe("Converting file " + cellFile + " failed. Exception: " + e.toString());
                            e.printStackTrace();
                        }
                    });

                    try {
                        osmpbWriter.flush();
                        stringTable.clear();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    Optional<String> relatedArea = areaRelations.get(areaKey);
                    if (relatedArea != null && relatedArea.isPresent()) {
                        double[] relatedBBox = grid2448.get(relatedArea.get());
                        int relatedGrid = geomUtils.xy2grid(relatedBBox[0], relatedBBox[1]);
                        nodeCache.removeGrid(relatedGrid);
                    }
                });

        osmpbWriter.closeOSMPBFFile();
    }

    private Map<String, Optional<String>> getRelatedAreas(Map<String, double[]> grid2448, Map<String, List<File>> areas) {
        return areas
                .keySet()
                .parallelStream()
                .filter(grid2448::containsKey)
                .collect(Collectors.toMap(
                        area -> area,
                        area -> {
                            double[] bbox = grid2448.get(area);
                            double minx = bbox[0];
                            double miny = bbox[2];

                            double searchx = minx + 24e3 - 2 * 48e3;
                            double searchy = miny + 12e3 - 2 * 24e3;
                            double[] search = new double[]{searchx, searchy};

                            return areas
                                    .keySet()
                                    .parallelStream()
                                    .filter(grid2448::containsKey)
                                    .filter(searchArea -> {
                                        double[] searchBBox = grid2448.get(searchArea);
                                        return geomUtils.pointInside(searchBBox, search);
                                    }).findFirst();
                        }));
    }

    private void initializeCachedDatasources() {
        cachedDatasources = new CachedAdditionalDataSources(conf);
    }

    private Stream<File> getMTKCellFiles(File mtkDirectory) {
        Collection<File> files = FileUtils.listFiles(
                mtkDirectory,
                new RegexFileFilter("^([A-Z0-9]{6})_mtk.zip"),
                DirectoryFileFilter.DIRECTORY
        );
        return files.stream().sorted();
    }

    MTKToGarminConverter(File configFile) {
        conf = readConfigFile(configFile);
        initializeOGR();
        readGridExtents();
        geomUtils = new GeomUtils();
        nodeCache = new NodeCache();

    }

    private Config readConfigFile(File configFile) {
        logger.info("Reading config");
        Config conf = ConfigFactory.parseFile(configFile);
        logger.info("CONFIG: " + conf.root().render());
        return conf;

    }

    private void initializeOGR() {
        logger.info("Initializing ogr");
        ogr.UseExceptions();
        Locale.setDefault(new Locale("en", "US"));
        ogr.RegisterAll();

        featurePreprocessMML = new MMLFeaturePreprocess();
        shapePreprocessor = new ShapeFeaturePreprocess();
    }

    private void readGridExtents() {
        DataSource gridds = ogr.Open(conf.getString("grid"));
        Layer lyr = gridds.GetLayer(0);

        lyr.SetAttributeFilter("gridSize = '12x12'");

        double[] extent = new double[4];
        FeatureDefn glyrdef = lyr.GetLayerDefn();
        int gridcellIndex = glyrdef.GetFieldIndex("gridCell");

        for (Feature feat = lyr.GetNextFeature(); feat != null; feat = lyr.GetNextFeature()) {
            Geometry geom = feat.GetGeometryRef();

            geom.GetEnvelope(extent);
            gridExtents.put(feat.GetFieldAsString(gridcellIndex), extent.clone());
            feat.delete();
        }
        lyr.delete();
        gridds.delete();
    }
}
