package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.util.ArrayList;
import java.util.Date;

class LightWay {
    final Int2IntOpenHashMap tags = new Int2IntOpenHashMap();
    long id;
    String role = "all";
    ArrayList<Long> refs = new ArrayList<>();

    public LightWay(long id) {
        this.id = id;
    }

    String getRole() {
        return role;
    }

    void setRole(String role) {
        this.role = role;
    }

    long getId() {
        return id;
    }

    public Int2IntOpenHashMap getTags() {
        return tags;
    }

    public WayContainer toOsmiumEntity(Long2ObjectOpenHashMap<LightNode> nodes, StringTable stringTable, Date timestamp) {
        CommonEntityData ced = new CommonEntityData(this.id, -1, timestamp, OsmUser.NONE, 0);
        tags.int2IntEntrySet().forEach(t -> ced.getTags().add(new Tag(stringTable.getStringById(t.getIntKey()), stringTable.getStringById(t.getIntValue()))));

        Way w = new Way(ced, refs.stream().map(nodeHash -> nodes.get(nodeHash.longValue()).toOsmiumWayNode()).toList());

        return new WayContainer(w);
    }
}
