package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;

import java.util.Arrays;

class ShapeSyvyysTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;

    private final StringTable stringtable;

    private final int depthContour, depthSounding, ele;

    ShapeSyvyysTagHandler(StringTable stringtable) {
        this.stringtable = stringtable;

        depthContour = stringtable.getStringId("VALDCO");
        depthSounding = stringtable.getStringId("DEPTH");
        ele = stringtable.getStringId("ele");

        wantedFields = new ObjectOpenHashSet<>(
                Arrays.asList("VALDCO", "DEPTH"));
    }

    @Override
    public ObjectOpenHashSet<String> getWantedFields() {
        return this.wantedFields;
    }

    @Override
    public void addElementTags(Int2IntMap tags, Int2ObjectMap<String> fields, String tyyppi, double geomarea) {
        for (Entry<String> k : fields.int2ObjectEntrySet()) {
            String val = k.getValue();
            int key = k.getIntKey();

            if (key == depthContour || key == depthSounding) {
                key = ele;
                val = String.format("%.1f", Float.parseFloat(val));

            }

            tags.put(key, this.stringtable.getStringId(val));
        }
    }

}
