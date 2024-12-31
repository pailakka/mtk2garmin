package org.hylly.mtk2garmin;


import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortMap;

interface TagHandlerI {

	ObjectOpenHashSet<String> getWantedFields();
	void addElementTags(Short2ShortMap tags, Short2ObjectOpenHashMap<String> fields, String tyyppi, double geomarea);
}
