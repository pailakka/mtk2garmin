package org.hylly.mtk2garmin;

import java.util.HashMap;
import java.util.Map;

public class Node {
    final long id;
    final double lon;
    final double lat;
    final long hash;
    Map<String, String> nodeTags;
    boolean waypart;

    Node(long hash, double lon, double lat, boolean waypart) {
        this.id = -1;
        this.hash = hash;
        this.lon = lon;
        this.lat = lat;
        this.waypart = waypart;
    }

    Node(long id, long hash, double lon, double lat, boolean waypart) {
        this.id = id;
        this.hash = hash;
        this.lon = lon;
        this.lat = lat;
        this.waypart = waypart;
    }

    public Node(long id, long hash, double lon, double lat, boolean waypart, Map<String, String> tags) {
        this.id = id;
        this.hash = hash;
        this.lon = lon;
        this.lat = lat;
        this.waypart = waypart;
        this.nodeTags = tags;
    }

    void clearTags() {
        if (nodeTags != null) {
            nodeTags.clear();
        }
    }

    void addTag(String key, String value) {
        if (nodeTags == null) {
            nodeTags = new HashMap<>();
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

    Map<String, String> getTags() {
        return nodeTags;
    }

}
