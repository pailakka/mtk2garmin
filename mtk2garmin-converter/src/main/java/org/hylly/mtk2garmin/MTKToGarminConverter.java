package org.hylly.mtk2garmin;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.gdal.ogr.ogr;
import org.mapdb.HTreeMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;


class MTKToGarminConverter {
    private final Logger logger = Logger.getLogger(MTKToGarminConverter.class.getName());
    private final Config conf;

    MTKToGarminConverter(File configFile) {
        conf = readConfigFile(configFile);
        initializeOGR();
    }

    void doConvert() {
        Path outDir = getOutDir();
        if (outDir == null) return;

        List<? extends Config> inputSources = conf.getConfigList("input");
        List<Double> bboxFilter = conf.getDoubleList("bboxFilter");

        HTreeMap<Long, Long> nodeIDs = null;
        NodeIDMapBuilder nodeIDMapBuilder = new NodeIDMapBuilder();
        if (conf.getBoolean("globalNodeIds")) {
            logger.info("Using global node IDs");
            nodeIDs = nodeIDMapBuilder.getNodeIDMap("global");
        }

        HTreeMap<Long, Long> finalNodeIDs = nodeIDs;
        inputSources.stream()
                .map(input -> new OGRSourceConverter(finalNodeIDs, outDir, bboxFilter, input.getString("key"), input))
                .forEach(OGRSourceConverter::convert);

        if (nodeIDs != null) {
            nodeIDMapBuilder.closeNodeIDMap();
        }
    }

    private Path getOutDir() {
        Path outDir = Paths.get(conf.getString("output"));

        if (!Files.exists(outDir)) {
            try {
                Files.createDirectories(outDir);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return outDir;
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
    }
}
