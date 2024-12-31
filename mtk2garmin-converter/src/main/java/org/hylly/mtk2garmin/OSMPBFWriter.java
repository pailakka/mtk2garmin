package org.hylly.mtk2garmin;

import crosby.binary.file.BlockOutputStream;
import crosby.binary.osmosis.OsmosisSerializer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;

public class OSMPBFWriter {

    private final FileOutputStream outputStream;
    private final OsmosisSerializer osmosisSerializer;
    private final Date timestamp = new Date();

    OSMPBFWriter(File outFile) throws FileNotFoundException {
        this.outputStream = new FileOutputStream(outFile);
        this.osmosisSerializer = new OsmosisSerializer(new BlockOutputStream(this.outputStream));
    }

    void startWritingOSMPBF() {
        this.osmosisSerializer.writeEmptyHeaderIfNeeded();
    }

    void writeOSMPBFElements(
            StringTable stringTable,
            Long2ObjectOpenHashMap<LightNode> nodes,
            Long2ObjectOpenHashMap<LightWay> ways,
            Long2ObjectOpenHashMap<LightRelation> relations
    ) {
        Stream<NodeContainer> osmiumNodes = nodes.long2ObjectEntrySet().stream().parallel().map(Map.Entry::getValue).sorted(Comparator.comparingLong(LightNode::getId)).map(n -> n.toOsmiumEntity(stringTable, timestamp));
        osmiumNodes.sequential().forEach(this.osmosisSerializer::process);

        Stream<WayContainer> osmiumsWays = ways.long2ObjectEntrySet().stream().parallel().map(Map.Entry::getValue).sorted(Comparator.comparingLong(LightWay::getId)).map(w -> w.toOsmiumEntity(nodes, stringTable, timestamp));
        osmiumsWays.sequential().forEach(this.osmosisSerializer::process);
        
        Stream<RelationContainer> osmiumRelations = relations.long2ObjectEntrySet().stream().map(Map.Entry::getValue).sorted(Comparator.comparingLong(LightRelation::getId)).map(r -> r.toOsmiumEntity(stringTable, timestamp));
        osmiumRelations.sequential().forEach(this.osmosisSerializer::process);
    }

    void closeOSMPBFFile() throws IOException {
        this.osmosisSerializer.complete();
        this.outputStream.close();
    }
}
