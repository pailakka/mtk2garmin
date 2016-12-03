package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class StringTable {
    private Object2ShortOpenHashMap<String> stringTableTranslate = new Object2ShortOpenHashMap<String>();
    private ObjectArrayList<String> stringTable = new ObjectArrayList<String>();
  
    
    public StringTable() {
        stringTable.add("");
    }

    public short getStringId(String stringKey) {
        if (!this.stringTableTranslate.containsKey(stringKey)) {
            short newIndex = (short)this.stringTable.size();
            this.stringTableTranslate.put(stringKey, newIndex);
            this.stringTable.add(newIndex, stringKey);
            return newIndex;
        } else {
            return this.stringTableTranslate.getShort(stringKey);
        }

    }

    
    public String getStringById(int id) {
        return this.stringTable.get(id);
    }

    public int getStringtableSize() {
        return stringTable.size();
    }
    
    
}
