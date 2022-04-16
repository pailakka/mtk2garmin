package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

import java.util.Map;

class ShapeSyvyysTagHandler implements TagHandler {
    private final StringTable stringtable;

    private final int depthContour, depthSounding, ele;

    ShapeSyvyysTagHandler(StringTable stringtable) {
        this.stringtable = stringtable;

        depthContour = stringtable.getStringId("VALDCO");
        depthSounding = stringtable.getStringId("DEPTH");
        ele = stringtable.getStringId("ele");
    }

    @Override
    public void addElementTags(Map<Integer, Integer> tags, Int2ObjectArrayMap<String> fields, String tyyppi, double geomarea) {
        fields.forEach((Integer kk, String val) -> {
            if (kk == depthContour || kk == depthSounding) {
                kk = ele;
                val = String.format("%.1f", Float.parseFloat(val));
            }
            tags.put(kk, this.stringtable.getStringId(val));
        });
    }

}
