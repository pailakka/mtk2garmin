package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

public class GenericTagHandler implements TagHandler {

    public GenericTagHandler() {

    }

    @Override
    public void addElementTags(Map<String, String> tags, Object2ObjectOpenHashMap<String, String> fields, String tyyppi, double geomarea) {
        fields.forEach((String kk, String val) -> {
            tags.put(kk, val);
        });

    }
}
