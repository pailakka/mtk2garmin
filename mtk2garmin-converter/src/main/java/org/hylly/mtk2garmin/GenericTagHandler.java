package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

import java.util.Map;

public class GenericTagHandler implements TagHandler {
    private final StringTable stringtable;

    public GenericTagHandler(StringTable stringTable) {
        this.stringtable = stringTable;
    }

    @Override
    public void addElementTags(Map<Integer, Integer> tags, Int2ObjectArrayMap<String> fields, String tyyppi, double geomarea) {
        fields.forEach((Integer kk, String val) -> {
            tags.put(kk, this.stringtable.getStringId(val));
        });

    }
}
