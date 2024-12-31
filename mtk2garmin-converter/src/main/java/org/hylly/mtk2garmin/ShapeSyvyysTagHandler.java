package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortMap;

import java.util.Arrays;

class ShapeSyvyysTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;

    private final StringTable stringtable;

    private final short depthContour, depthSounding, ele;

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
    public void addElementTags(Short2ShortMap tags, Short2ObjectOpenHashMap<String> fields, String tyyppi, double geomarea) {
        for (Entry<String> k : fields.short2ObjectEntrySet()) {
            String val = k.getValue();
            short key = k.getShortKey();

            if (key == depthContour || key == depthSounding) {
                key = ele;
                val = String.format("%.1f", Float.parseFloat(val));

            }

            tags.put(key, this.stringtable.getStringId(val));
        }
    }

}
