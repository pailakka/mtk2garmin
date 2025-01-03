package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.Date;

class LightNode {
    final long id;
    final double lon;
    final double lat;
    final long hash;
    Int2IntMap nodeTags;
    boolean wayPart;

    LightNode(long id, long hash, double lon, double lat, boolean wayPart) {
        this.id = id;
        this.hash = hash;
        this.lon = lon;
        this.lat = lat;
        this.wayPart = wayPart;
    }

    void addTag(int key, int value) {
        if (nodeTags == null) {
            nodeTags = new Int2IntOpenHashMap();
        }

        nodeTags.put(key, value);
    }

    long getId() {
        return id;
    }

    boolean isWayPart() {
        return wayPart;
    }

    long getHash() {
        return hash;
    }

    public NodeContainer toOsmiumEntity(StringTable stringTable, Date timestamp) {
        CommonEntityData ced = new CommonEntityData(this.id, -1, timestamp, OsmUser.NONE, 0);
        if (nodeTags != null) {
            nodeTags.int2IntEntrySet().forEach(t -> ced.getTags().add(new Tag(stringTable.getStringById(t.getIntKey()), stringTable.getStringById(t.getIntValue()))));
        }

        return new NodeContainer(new Node(ced, this.lat, this.lon));
    }

    public WayNode toOsmiumWayNode() {
        return new WayNode(this.id, this.lat, this.lon);
    }
}
