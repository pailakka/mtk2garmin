package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;

import javax.swing.text.html.Option;
import java.util.Optional;
import java.util.logging.Logger;

class NodeCache {
    private Logger logger = Logger.getLogger(MTKToGarminConverter.class.getName());
    private final Int2ObjectMap<Long2LongMap> nodeCache;


    NodeCache() {
        Int2ObjectMap<Long2LongMap> unsafeNodeCache = new Int2ObjectAVLTreeMap<>();
        nodeCache = Int2ObjectMaps.synchronize(unsafeNodeCache);
    }

    void ensureGrid(int grid) {
        if (!nodeCache.containsKey(grid)) {
            Long2LongAVLTreeMap unsafeGridMap = new Long2LongAVLTreeMap();
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
