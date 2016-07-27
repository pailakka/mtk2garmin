package org.hylly.mtk2garmin;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class ShapeRetkeilyTagHandler implements TagHandlerI {
	public final ObjectOpenHashSet<String> wantedFields = new ObjectOpenHashSet<String>(
			Arrays.asList("name_fi", "category_i"));

	@Override
	public ObjectOpenHashSet<String> getWantedFields() {
		return this.wantedFields;
	}

	@Override
	public void addElementTags(Int2IntRBTreeMap tags, Int2ObjectOpenHashMap<String> fields) {
		for (Entry<String> k : fields.int2ObjectEntrySet()) {
			tags.put(
					k.getIntKey(),
					MTKToGarminConverter.getStringId(k.getValue())
					);
		}
	}

}
