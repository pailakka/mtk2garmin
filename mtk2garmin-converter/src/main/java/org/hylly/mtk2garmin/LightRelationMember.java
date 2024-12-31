package org.hylly.mtk2garmin;

import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;

class LightRelationMember {
    private final long id;
    private final String role;

    public LightRelationMember(long id, String role) {
        this.id = id;
        this.role = role;
    }

    public RelationMember toOsmiumEntity() {
        return new RelationMember(this.id, EntityType.Way, this.role);
    }
}
