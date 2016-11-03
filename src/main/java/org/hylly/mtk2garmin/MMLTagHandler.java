package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import static java.util.Arrays.asList;


class MMLTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;
    
    private int korarvo = MTKToGarminConverter.getStringId("korkeusarvo");
    private int syvarvo = MTKToGarminConverter.getStringId("syvyysarvo");
    private int nimisuomi = MTKToGarminConverter.getStringId("nimi_suomi");
    private int nimiruotsi = MTKToGarminConverter.getStringId("nimi_ruotsi");
    private int teksti = MTKToGarminConverter.getStringId("teksti");
    private int teksti_kieli = MTKToGarminConverter.getStringId("teksti_kieli");    
    private int tienro = MTKToGarminConverter.getStringId("tienumero");
    private int tasosij = MTKToGarminConverter.getStringId("tasosijainti");
	private int bridge = MTKToGarminConverter.getStringId("bridge");
	private int tunnel = MTKToGarminConverter.getStringId("tunnel");
	private int yes = MTKToGarminConverter.getStringId("yes");
	
	private int ele = MTKToGarminConverter.getStringId("ele");
	private int name = MTKToGarminConverter.getStringId("name");
	private int ref = MTKToGarminConverter.getStringId("ref");
	private int fin = MTKToGarminConverter.getStringId("fin");
	

    MMLTagHandler() {
        wantedFields = new ObjectOpenHashSet<String>(asList("nimi_suomi", "kohdeluokka", "yksisuuntaisuus", "tienumero", "korkeusarvo", "tasosijainti", "syvyysarvo", "valmiusaste", "paallyste", "teksti","teksti_kieli"));
    }

    @Override
    public ObjectOpenHashSet<String> getWantedFields() {
        return this.wantedFields;
    }

    @Override
    public void addElementTags(Int2IntRBTreeMap tags, Int2ObjectOpenHashMap<String> fields, String tyyppi) {
    	if (tags.get(teksti_kieli) == fin && fields.get(teksti_kieli) != "fin") {
    		return;
    	}
    	
        for (Entry<String> k : fields.int2ObjectEntrySet()) {
            
        	int kk = k.getIntKey();
            String val = k.getValue();
            if (kk == korarvo || kk == syvarvo) {
                Double korarvo = (Integer.parseInt(val) / 1000.0);
                kk = ele;
                val = String.format("%.1f", korarvo);
            }

            if (kk == nimisuomi || kk == teksti) {
                kk = name;
            }
            
            if (kk == nimiruotsi) {
                if (!tags.containsKey(name)) {
                    kk = name;
                } else {
                    continue;
                }
            }

            if (kk == tienro) {
                kk = ref;
            }

            if (kk == tasosij) {
            	int sijval = Integer.parseInt(val);
            	if (sijval > 0) {
                  tags.put(bridge, yes);
            	} else if (sijval < 0) {
                tags.put(tunnel, yes);
            	}
            }

            tags.put(kk, MTKToGarminConverter.getStringId(val));
        }
        
        if (tyyppi.equals("sahkolinja")) {
        	tags.remove(bridge);
        }

    }

}
