package org.hylly.mtk2garmin;

import java.util.Collections;

import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

class ShapeSyvyysTagHandler implements TagHandlerI {
	private final ObjectOpenHashSet<String> wantedFields;

	ShapeSyvyysTagHandler() {
		wantedFields = new ObjectOpenHashSet<String>(Collections.singletonList("DEPTH"));
	}

	@Override
	public ObjectOpenHashSet<String> getWantedFields() {
		return this.wantedFields;
	}
	@Override
	public void addElementTags(Int2IntRBTreeMap tags, Int2ObjectOpenHashMap<String> fields) {
		for (Entry<String> k : fields.int2ObjectEntrySet()) {
			String val = k.getValue();
			int key = k.getIntKey();
			
			if (MTKToGarminConverter.getStringById(key).equals("DEPTH")) {
				val = String.format("%.1f", Float.parseFloat(val));
				
			}

			tags.put(
					key,
					MTKToGarminConverter.getStringId(val)
					);
		}
	}

}
