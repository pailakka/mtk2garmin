package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

class StringTable {
    private final Object2IntOpenHashMap<String> stringTableTranslate = new Object2IntOpenHashMap<>();
    private final ObjectArrayList<String> stringTable = new ObjectArrayList<>();


    StringTable() {
        stringTable.add("");
    }

    int getStringId(String stringKey) {
        if (!this.stringTableTranslate.containsKey(stringKey)) {
            int newIndex = this.stringTable.size();
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

    ObjectArrayList<String> getStringTable() {
        return stringTable;
    }

    int getStringtableSize() {
        return stringTable.size();
    }


}
