package org.hylly.mtk2garmin;


import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

import java.util.Map;

interface TagHandler {
    void addElementTags(Map<Integer, Integer> tags, Int2ObjectArrayMap<String> fields, String tyyppi, double geomarea);
}
