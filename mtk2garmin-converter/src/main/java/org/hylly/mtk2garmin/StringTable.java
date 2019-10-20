package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

class StringTable {
    private final Object2ShortOpenHashMap<String> stringTableTranslate = new Object2ShortOpenHashMap<>();
    private final ObjectArrayList<String> stringTable = new ObjectArrayList<>();


    StringTable() {
        stringTable.add("");
    }

    short getStringId(String stringKey) {
        if (!this.stringTableTranslate.containsKey(stringKey)) {
            short newIndex = (short) this.stringTable.size();
            this.stringTableTranslate.put(stringKey, newIndex);
            this.stringTable.add(newIndex, stringKey);
            return newIndex;
        } else {
            return this.stringTableTranslate.getShort(stringKey);
        }

    }


    String getStringById(int id) {
        return this.stringTable.get(id);
    }
    ObjectArrayList<String>  getStringTable() {
        return stringTable;
    }

    int getStringtableSize() {
        return stringTable.size();
    }


}
