package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

@Deprecated
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

    void writeOSMPBFElements(StringTable stringtable, Long2ObjectOpenHashMap<Node> nodes, Long2ObjectOpenHashMap<Way> ways, Long2ObjectOpenHashMap<Relation> relations) throws IOException {
        op.writePBFElements(stringtable, nodes.values().stream(), null, null);
        op.writePBFElements(stringtable, null, ways.values().stream(), null);
        op.writePBFElements(stringtable, null, null, relations.values().stream());
    }

    void writeOSMPBFElements(StringTable stringtable, Stream<Node> nodes, Stream<Way> ways, Stream<Relation> relations) throws IOException {
        op.writePBFElements(stringtable, nodes, null, null);
        op.writePBFElements(stringtable, null, ways, null);
        op.writePBFElements(stringtable, null, null, relations);
    }

    void closeOSMPBFFile() throws IOException {
        op.closePBF();
    }
}
