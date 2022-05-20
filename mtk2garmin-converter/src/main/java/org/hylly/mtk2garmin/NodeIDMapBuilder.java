package org.hylly.mtk2garmin;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerCompressionWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.Files.deleteIfExists;

public class NodeIDMapBuilder {
    private final Logger logger = Logger.getLogger(MTKToGarminConverter.class.getName());

    private DB dbDisk;
    private HTreeMap<Long, Long> nodeIDs;
    private List<String> openedDBs = new ArrayList<>();

    @NotNull
    public HTreeMap<Long, Long> getNodeIDMap(String name) {
        logger.info("Opening node map nodes2 / onDisk-nodes");
        String dbName = "nodes" + name;
        try {
            deleteIfExists(Path.of(dbName));
        } catch (IOException e) {
            logger.severe("Failed to delete existing dbfile " + dbName);
        }
        dbDisk = DBMaker
                .fileDB(dbName)
                .fileMmapEnable()
                .fileDeleteAfterClose()
                .fileDeleteAfterOpen()
                .closeOnJvmShutdown()
                .make();

        nodeIDs = dbDisk
                .hashMap("onDisk-" + name)
                .keySerializer(new SerializerCompressionWrapper<>(Serializer.LONG))
                .valueSerializer(new SerializerCompressionWrapper<>(Serializer.LONG))
                .create();
        this.openedDBs.add(dbName);
        return nodeIDs;
    }

    public void closeNodeIDMap() {
        logger.info("Closing node map nodes2 / onDisk-nodes");
        this.nodeIDs.close();
        this.dbDisk.close();
        this.nodeIDs = null;
        this.dbDisk = null;

        this.openedDBs = this.openedDBs.stream().map(fn -> {
            File f = new File(fn);
            if (f.delete()) {
                return null;
            }
            return fn;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
