package org.hylly.mtk2garmin;

import java.util.Map;

public class GenericTagHandler implements TagHandler {
    private final StringTable stringtable;

    public GenericTagHandler(StringTable stringTable) {
        this.stringtable = stringTable;
    }

    @Override
    public void addElementTags(Map<Short, Short> tags, Map<Short, String> fields, String tyyppi, double geomarea) {
        for (Map.Entry<Short, String> k : fields.entrySet()) {
            short kk = k.getKey();
            String val = k.getValue();
            tags.put(kk, this.stringtable.getStringId(val));
        }
    }
}
