package org.hylly.mtk2garmin;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

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
    public void addElementTags(Int2IntRBTreeMap tags, Int2ObjectOpenHashMap<String> fields) {
        for (Entry<String> k : fields.int2ObjectEntrySet()) {
            String ks = MTKToGarminConverter.getStringById(k.getIntKey()).intern();
            String val = k.getValue();

            if (ks.equals("name_fi")) {
                ks = "name";
            }

            tags.put(MTKToGarminConverter.getStringId(ks), MTKToGarminConverter.getStringId(val));
        }

    }
}

