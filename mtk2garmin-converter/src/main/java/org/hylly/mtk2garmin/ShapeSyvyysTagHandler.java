package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

class ShapeSyvyysTagHandler implements TagHandler {
    private final String depthContour, depthSounding, ele;

    ShapeSyvyysTagHandler() {
        depthContour = "VALDCO";
        depthSounding = "DEPTH";
        ele = "ele";
    }

    @Override
    public void addElementTags(Map<String, String> tags, Object2ObjectOpenHashMap<String, String> fields, String tyyppi, double geomarea) {
        fields.forEach((String kk, String val) -> {
            if (kk.equals(depthContour) || kk.equals(depthSounding)) {
                kk = ele;
                val = String.format("%.1f", Float.parseFloat(val));
            }
            tags.put(kk, val);
        });
    }

}
