package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.util.Optional;
import java.util.logging.Logger;

class NodeCache {
    private final Logger logger = Logger.getLogger(NodeCache.class.getName());
    private final Int2ObjectMap<Long2LongMap> nodeCache;


    NodeCache() {
        Int2ObjectMap<Long2LongMap> unsafeNodeCache = new Int2ObjectOpenHashMap<>();
        nodeCache = Int2ObjectMaps.synchronize(unsafeNodeCache);
    }

    void ensureGrid(int grid) {
        if (!nodeCache.containsKey(grid)) {
            Long2LongMap unsafeGridMap = new Long2LongOpenHashMap();
            nodeCache.put(grid, Long2LongMaps.synchronize(unsafeGridMap));
            logger.info(grid + " added to nodecache");
        }
    }

    void removeGrid(int grid) {
        if (nodeCache.containsKey(grid)) {
            nodeCache.remove(grid);
            logger.info(grid + " removed from nodecache");
        }
    }

    Optional<Long> getNodeId(int pcell, long phash) {
        if (nodeCache.containsKey(pcell)) {
            Long2LongMap areaCache = nodeCache.get(pcell);
            if (areaCache.containsKey(phash)) {
                return Optional.of(areaCache.get(phash));
            }
        }
        return Optional.empty();
    }

    void addNodeId(int pcell, long phash, long nodeid) {
        ensureGrid(pcell);
        nodeCache.get(pcell).put(phash, nodeid);
    }
}
