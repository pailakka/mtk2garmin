package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.Objects;

//Deprecated until outdoors data available
@Deprecated
class ShapeRetkeilyTagHandler implements TagHandler {

    ShapeRetkeilyTagHandler() {
    }

    @Override
    public void addElementTags(Map<String, String> tags, Object2ObjectOpenHashMap<String, String> fields, String tyyppi, double geomarea) {
        for (Map.Entry<String, String> k : fields.entrySet()) {
            String kk = k.getKey();
            String val = k.getValue();

            String namefi = "name_fi";
            if (Objects.equals(kk, namefi)) {
                kk = "name";
            }

            tags.put(kk, val);
        }

    }
}

