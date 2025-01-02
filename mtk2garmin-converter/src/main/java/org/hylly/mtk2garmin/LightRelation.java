package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import java.util.ArrayList;
import java.util.Date;

class LightRelation {
    final Int2IntOpenHashMap tags = new Int2IntOpenHashMap();
    final ArrayList<LightRelationMember> members = new ArrayList<>();
    long id;

    long getId() {
        return id;
    }

    void setId(long id) {
        this.id = id;
    }

    public RelationContainer toOsmiumEntity(StringTable stringTable, Date timestamp) {
        CommonEntityData ced = new CommonEntityData(this.id, -1, timestamp, OsmUser.NONE, 0);
        tags.int2IntEntrySet().forEach(t -> ced.getTags().add(new Tag(stringTable.getStringById(t.getIntKey()), stringTable.getStringById(t.getIntValue()))));

        Relation r = new Relation(ced, this.members.stream().map(LightRelationMember::toOsmiumEntity).toList());

        return new RelationContainer(r);

    }
}
