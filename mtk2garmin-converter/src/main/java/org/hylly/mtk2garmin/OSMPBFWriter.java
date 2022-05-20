package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

public class OSMPBFWriter {

    private final File outFile;
    private final OSMPBF op;

    OSMPBFWriter(File outFile) {
        this.outFile = outFile;
        op = new OSMPBF(this.outFile);
    }

    void startWritingOSMPBF() throws IOException {
        op.writePBFHeaders();
    }

    void writeOSMPBFElements(Long2ObjectOpenHashMap<Node> nodes, Long2ObjectOpenHashMap<Way> ways, Long2ObjectOpenHashMap<Relation> relations) throws IOException {
        op.writePBFElements(nodes.values().stream(), null, null);
        op.writePBFElements(null, ways.values().stream(), null);
        op.writePBFElements(null, null, relations.values().stream());
    }

    void writeOSMPBFElements(Stream<Node> nodes, Stream<Way> ways, Stream<Relation> relations) throws IOException {
        op.writePBFElements(nodes, null, null);
        op.writePBFElements(null, ways, null);
        op.writePBFElements(null, null, relations);
    }

    void closeOSMPBFFile() throws IOException {
        op.closePBF();
    }
}
