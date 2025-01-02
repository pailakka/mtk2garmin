package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

class StringTable {
    private final Object2IntMap<String> stringTableTranslateBase = new Object2IntOpenHashMap<>();
    private final ObjectArrayList<String> stringTable = new ObjectArrayList<>();
    private final Object2IntMap<String> stringTableTranslate;

    StringTable() {
        stringTable.add("");
        stringTableTranslate = Object2IntMaps.synchronize(stringTableTranslateBase);
    }

    int getStringId(String stringKey) {
        if (!this.stringTableTranslate.containsKey(stringKey)) {
            int newIndex = (int) this.stringTable.size();
            this.stringTableTranslate.put(stringKey, newIndex);
            this.stringTable.add(newIndex, stringKey);
            return newIndex;
        } else {
            return this.stringTableTranslate.getInt(stringKey);
        }
    }

    String getStringById(int id) {
        return this.stringTable.get(id);
    }


    public void clear() {
        this.stringTableTranslate.clear();
        this.stringTable.clear();
    }
}
