package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

class Way {
    final Short2ShortRBTreeMap tags = new Short2ShortRBTreeMap();
    long id;
    String role = "all";
    LongArrayList refs = new LongArrayList();

    Way() {

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

    public Short2ShortRBTreeMap getTags() {
        return tags;
    }
}
