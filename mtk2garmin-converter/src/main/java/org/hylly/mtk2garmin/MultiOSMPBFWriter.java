package org.hylly.mtk2garmin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class MultiOSMPBFWriter {
    private final Logger logger = Logger.getLogger(MTKToGarminConverter.class.getName());
    private final OSMPBF nodeOutput;
    private final OSMPBF wayOutput;
    private final OSMPBF relationOutput;
    private final File mergedFile;
    private final File nodeFile;
    private final File wayFile;
    private final File relationFile;

    public MultiOSMPBFWriter(Path outDir, String inputKey) {
        this.nodeFile = new File(outDir.resolve(String.format("%s_nodes.osm.pbf", inputKey)).toString());
        this.wayFile = new File(outDir.resolve(String.format("%s_ways.osm.pbf", inputKey)).toString());
        this.relationFile = new File(outDir.resolve(String.format("%s_relations.osm.pbf", inputKey)).toString());

        this.mergedFile = new File(outDir.resolve(String.format("%s.osm.pbf", inputKey)).toString());

        this.nodeOutput = new OSMPBF(this.nodeFile);
        this.wayOutput = new OSMPBF(this.wayFile);
        this.relationOutput = new OSMPBF(this.relationFile);
    }

    void writeNodes(Stream<Node> nodes) throws IOException {
        this.nodeOutput.writePBFElements(nodes, null, null);
    }

    void writeWays(Stream<Way> ways) throws IOException {
        this.wayOutput.writePBFElements(null, ways, null);
    }

    void writeRelations(Stream<Relation> relations) throws IOException {
        this.relationOutput.writePBFElements(null, null, relations);
    }

    void closeMultiWriter() throws IOException {
        this.nodeOutput.closePBF();
        this.wayOutput.closePBF();
        this.relationOutput.closePBF();
    }

    void writeMergedFile() throws IOException {
        logger.info("Writing merged osm pbf file to " + mergedFile.toString());
        OSMPBF mergedOutput = new OSMPBF(mergedFile);
        mergedOutput.writePBFHeaders();
        mergedOutput.closePBF();
        try (FileOutputStream mergedOutputStream = new FileOutputStream(mergedFile, true)) {
            logger.info("Copying nodes from " + nodeFile.toString());
            streamCopyFile(mergedOutputStream, this.nodeFile);
            logger.info("Copying ways from " + wayFile.toString());
            streamCopyFile(mergedOutputStream, this.wayFile);
            logger.info("Copying relations from " + relationFile.toString());
            streamCopyFile(mergedOutputStream, this.relationFile);
        }
    }

    private void streamCopyFile(FileOutputStream outStream, File fromFile) throws IOException {
        Files.copy(fromFile.toPath(), outStream);
    }
}
