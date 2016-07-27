package org.hylly.mtk2garmin;


import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public interface TagHandlerI {

	public ObjectOpenHashSet<String> getWantedFields();
	public void addElementTags(Int2IntRBTreeMap tags, Int2ObjectOpenHashMap<String> fields);
}
