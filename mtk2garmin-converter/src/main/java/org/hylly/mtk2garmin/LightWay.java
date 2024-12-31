package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortOpenHashMap;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.util.ArrayList;
import java.util.Date;

class LightWay {
    final Short2ShortOpenHashMap tags = new Short2ShortOpenHashMap();
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

    public Short2ShortOpenHashMap getTags() {
        return tags;
    }

    public WayContainer toOsmiumEntity(Long2ObjectOpenHashMap<LightNode> nodes, StringTable stringTable, Date timestamp) {
        CommonEntityData ced = new CommonEntityData(this.id, -1, timestamp, OsmUser.NONE, 0);
        tags.short2ShortEntrySet().forEach(t -> ced.getTags().add(new Tag(stringTable.getStringById(t.getShortKey()), stringTable.getStringById(t.getShortValue()))));

        Way w = new Way(ced, refs.stream().map(nodeHash -> nodes.get(nodeHash.longValue()).toOsmiumWayNode()).toList());

        return new WayContainer(w);
    }
}
