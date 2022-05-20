package org.hylly.mtk2garmin;


import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

interface TagHandler {
    void addElementTags(Map<String, String> tags, Object2ObjectOpenHashMap<String, String> fields, String tyyppi, double geomarea);
}
