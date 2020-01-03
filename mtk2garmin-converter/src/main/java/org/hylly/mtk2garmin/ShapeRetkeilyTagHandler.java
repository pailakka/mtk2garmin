package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

import java.util.Arrays;

class ShapeRetkeilyTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;
    private final StringTable stringtable;

    private final short namefi, name;
    
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
    public void addElementTags(Short2ShortRBTreeMap tags, Short2ObjectOpenHashMap<String> fields, String tyyppi, double geomarea) {
        for (Entry<String> k : fields.short2ObjectEntrySet()) {
            String val = k.getValue();
            
            short ok = k.getShortKey();
            
            if (ok == namefi) {
                ok = name;
            }

            tags.put(ok, this.stringtable.getStringId(val));
        }

    }
}

