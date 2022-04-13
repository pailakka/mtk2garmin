package org.hylly.mtk2garmin;

import java.util.Map;

class ShapeSyvyysTagHandler implements TagHandler {
    private final StringTable stringtable;

    private final short depthContour, depthSounding, ele;

    ShapeSyvyysTagHandler(StringTable stringtable) {
        this.stringtable = stringtable;

        depthContour = stringtable.getStringId("VALDCO");
        depthSounding = stringtable.getStringId("DEPTH");
        ele = stringtable.getStringId("ele");
    }

    @Override
    public void addElementTags(Map<Short, Short> tags, Map<Short, String> fields, String tyyppi, double geomarea) {
        for (Map.Entry<Short, String> k : fields.entrySet()) {
            short kk = k.getKey();
            String val = k.getValue();

            if (kk == depthContour || kk == depthSounding) {
                kk = ele;
                val = String.format("%.1f", Float.parseFloat(val));

            }

            tags.put(kk, this.stringtable.getStringId(val));
        }
    }

}
