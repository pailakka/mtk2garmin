package org.hylly.mtk2garmin;


import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

interface TagHandlerI {

	ObjectOpenHashSet<String> getWantedFields();
	void addElementTags(Int2IntMap tags, Int2ObjectMap<String> fields, String tyyppi, double geomarea);
}
