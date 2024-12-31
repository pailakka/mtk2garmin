package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import java.util.ArrayList;
import java.util.Date;

class LightRelation {
    final Short2ShortRBTreeMap tags = new Short2ShortRBTreeMap();
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
        tags.short2ShortEntrySet().forEach(t -> ced.getTags().add(new Tag(stringTable.getStringById(t.getShortKey()), stringTable.getStringById(t.getShortValue()))));

        Relation r = new Relation(ced, this.members.stream().map(LightRelationMember::toOsmiumEntity).toList());

        return new RelationContainer(r);

    }
}
