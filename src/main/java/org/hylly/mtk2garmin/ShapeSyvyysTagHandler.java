package org.hylly.mtk2garmin;

import java.util.Collections;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

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
	public void addElementTags(Short2ShortRBTreeMap tags, Short2ObjectOpenHashMap<String> fields, String tyyppi) {
		for (Entry<String> k : fields.short2ObjectEntrySet()) {
			String val = k.getValue();
			short key = k.getShortKey();
			
			if (MTKToGarminConverter.getStringById(key).equals("DEPTH")) {
                key = MTKToGarminConverter.getStringId("ele");
				val = String.format("%.1f", Float.parseFloat(val));
				
			}

			tags.put(
					key,
					MTKToGarminConverter.getStringId(val)
					);
		}
	}

}
