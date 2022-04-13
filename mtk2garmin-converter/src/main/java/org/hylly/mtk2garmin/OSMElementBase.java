package org.hylly.mtk2garmin;

import java.util.HashMap;
import java.util.Map;

public class OSMElementBase {
    final Map<Short, Short> tags = new HashMap<>();
    long id;

    long getId() {
        return id;
    }

    void setId(long id) {
        this.id = id;
    }

    public Map<Short, Short> getTags() {
        return tags;
    }
}
