package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;

import java.util.Arrays;

class ShapeRetkeilyTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;
    private final StringTable stringtable;

    private final int namefi, name;
    
    ShapeRetkeilyTagHandler(StringTable stringtable) {
        this.stringtable = stringtable;
        
        namefi = this.stringtable.getStringId("name_fi");
        name = this.stringtable.getStringId("name");

        wantedFields = new ObjectOpenHashSet<>(
                Arrays.asList("name_fi", "category_i", "cat_id"));
    }

    @Override
    public ObjectOpenHashSet<String> getWantedFields() {
        return this.wantedFields;
    }

    @Override
    public void addElementTags(Int2IntMap tags, Int2ObjectMap<String> fields, String tyyppi, double geomarea) {
        for (Entry<String> k : fields.int2ObjectEntrySet()) {
            String val = k.getValue();
            
            int ok = k.getIntKey();
            
            if (ok == namefi) {
                ok = name;
            }

            tags.put(ok, this.stringtable.getStringId(val));
        }

    }
}

