package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Arrays;
import java.util.Map;

//Deprecated until outdoors data available
@Deprecated
class ShapeRetkeilyTagHandler implements TagHandler {
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
    public void addElementTags(Map<Short, Short> tags, Map<Short, String> fields, String tyyppi, double geomarea) {
        for (Map.Entry<Short, String> k : fields.entrySet()) {
            short kk = k.getKey();
            String val = k.getValue();

            if (kk == namefi) {
                kk = name;
            }

            tags.put(kk, this.stringtable.getStringId(val));
        }

    }
}

