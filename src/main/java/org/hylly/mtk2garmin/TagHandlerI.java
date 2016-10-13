package org.hylly.mtk2garmin;


import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

interface TagHandlerI {

	ObjectOpenHashSet<String> getWantedFields();
	void addElementTags(Int2IntRBTreeMap tags, Int2ObjectOpenHashMap<String> fields);
}
