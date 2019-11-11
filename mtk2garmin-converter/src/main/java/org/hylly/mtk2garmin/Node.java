package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

public class Node {
    final long id;
    final double lon;
    final double lat;
    final int cell;
    final long hash;
    Short2ShortRBTreeMap nodeTags;
    boolean waypart;

    Node(long id, long hash, int cell, double lon, double lat, boolean waypart) {
        this.id = id;
        this.hash = hash;
        this.lon = lon;
        this.lat = lat;
        this.cell = cell;
        this.waypart = waypart;
    }

    public Node(long id, long hash, int cell, double lon, double lat, boolean waypart, Short2ShortRBTreeMap tags) {
        this.id = id;
        this.hash = hash;
        this.lon = lon;
        this.lat = lat;
        this.cell = cell;
        this.waypart = waypart;
        this.nodeTags = tags;
    }

    void clearTags() {
        if (nodeTags != null) {
            nodeTags.clear();
        }
    }

    void addTag(short key, short value) {
        if (nodeTags == null) {
            nodeTags = new Short2ShortRBTreeMap();
        }

        nodeTags.put(key, value);
    }

    long getId() {
        return id;
    }

    double getLon() {
        return lon;
    }

    double getLat() {
        return lat;
    }

    boolean isWaypart() {
        return waypart;
    }

    long getHash() {
        return hash;
    }

    Short2ShortRBTreeMap getTags() {
        return nodeTags;
    }

}
