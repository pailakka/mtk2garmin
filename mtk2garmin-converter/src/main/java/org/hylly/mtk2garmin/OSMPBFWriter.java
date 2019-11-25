package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.File;
import java.io.IOException;

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
        op.writePBFElements(stringtable, Boolean.FALSE, nodes, null, null);
        op.writePBFElements(stringtable, Boolean.FALSE, null, ways, null);
        op.writePBFElements(stringtable, Boolean.FALSE, null, null, relations);

        // this.initElements();
    }

    void closeOSMPBFFile() throws IOException {
        op.closePBF();
    }
}
