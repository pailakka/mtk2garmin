package org.hylly.mtk2garmin;

import java.util.Arrays;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

class ShapeRetkeilyTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;

    ShapeRetkeilyTagHandler() {
        wantedFields = new ObjectOpenHashSet<String>(
                Arrays.asList("name_fi", "category_i", "cat_id"));
    }

    @Override
    public ObjectOpenHashSet<String> getWantedFields() {
        return this.wantedFields;
    }

    @Override
    public void addElementTags(Short2ShortRBTreeMap tags, Short2ObjectOpenHashMap<String> fields, String tyyppi) {
        for (Entry<String> k : fields.short2ObjectEntrySet()) {
            String ks = MTKToGarminConverter.getStringById(k.getShortKey()).intern();
            String val = k.getValue();

            if (ks.equals("name_fi")) {
                ks = "name";
            }

            tags.put(MTKToGarminConverter.getStringId(ks), MTKToGarminConverter.getStringId(val));
        }

    }
}

