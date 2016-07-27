package org.hylly.mtk2garmin;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;


public class MMLTagHandler implements TagHandlerI {
	public final ObjectOpenHashSet<String> wantedFields = new ObjectOpenHashSet<String>(Arrays.asList("nimi_suomi","kohdeluokka","yksisuuntaisuus","kulkukelpoisuus","tienumero","korkeusarvo","valmiusaste","paallyste","teksti"));
	
	@Override
	public ObjectOpenHashSet<String> getWantedFields() {
		return this.wantedFields;
	}
	@Override
	public void addElementTags(Int2IntRBTreeMap tags, Int2ObjectOpenHashMap<String> fields) {

        for (Entry<String> k : fields.int2ObjectEntrySet()) {
            String ks = MTKToGarminConverter.getStringById(k.getIntKey()).intern();
            String val = k.getValue();
           
            if (ks.equals("korkeusarvo") || ks.equals("syvyysarvo")) {
                Double korarvo = (Integer.parseInt(val) / 1000.0);
                ks = "ele";
                val = String.format("%.1f", korarvo);
            }

            if (ks.equals("nimi_suomi") || ks.equals("teksti")) {
                ks = "name";
            }

            if (ks.equals("tienumero")) {
                ks = "ref";
            }

            if ("tasosijainti".equals(ks) && Integer.parseInt(val) > 0) {
                tags.put(MTKToGarminConverter.getStringId("bridge"), MTKToGarminConverter.getStringId("yes"));
            }

            if ("tasosijainti".equals(ks) && Integer.parseInt(val) < 0) {
                tags.put(MTKToGarminConverter.getStringId("tunnel"), MTKToGarminConverter.getStringId("yes"));
            }

            tags.put(k.getIntKey(), MTKToGarminConverter.getStringId(val));
        }
		
	}

}
