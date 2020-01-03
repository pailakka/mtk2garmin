package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

import java.util.ArrayList;

class Relation {
    final Short2ShortRBTreeMap tags = new Short2ShortRBTreeMap();
    final ArrayList<RelationMember> members = new ArrayList<>();
    long id;

    long getId() {
        return id;
    }

    void setId(long id) {
        this.id = id;
    }

    Short2ShortRBTreeMap getTags() {
        return tags;
    }

    ArrayList<RelationMember> getMembers() {
        return members;
    }
}
