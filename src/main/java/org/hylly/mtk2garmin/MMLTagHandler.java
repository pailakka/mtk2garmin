package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

import static java.util.Arrays.asList;


class MMLTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;
    
    private short korarvo = MTKToGarminConverter.getStringId("korkeusarvo");
    private short syvarvo = MTKToGarminConverter.getStringId("syvyysarvo");
    private short nimisuomi = MTKToGarminConverter.getStringId("nimi_suomi");
    private short nimiruotsi = MTKToGarminConverter.getStringId("nimi_ruotsi");
    private short teksti = MTKToGarminConverter.getStringId("teksti");
    private short teksti_kieli = MTKToGarminConverter.getStringId("teksti_kieli");    
    private short tienro = MTKToGarminConverter.getStringId("tienumero");
    private short tasosij = MTKToGarminConverter.getStringId("tasosijainti");
	private short bridge = MTKToGarminConverter.getStringId("bridge");
	private short tunnel = MTKToGarminConverter.getStringId("tunnel");
	private short yes = MTKToGarminConverter.getStringId("yes");
	
	private short ele = MTKToGarminConverter.getStringId("ele");
	private short name = MTKToGarminConverter.getStringId("name");
	private short ref = MTKToGarminConverter.getStringId("ref");
	private short fin = MTKToGarminConverter.getStringId("fin");
	

    MMLTagHandler() {
        wantedFields = new ObjectOpenHashSet<String>(asList("nimi_ruotsi","nimi_suomi", "kohdeluokka", "yksisuuntaisuus", "tienumero", "korkeusarvo", "tasosijainti", "syvyysarvo", "valmiusaste", "paallyste", "teksti","teksti_kieli"));
    }

    @Override
    public ObjectOpenHashSet<String> getWantedFields() {
        return this.wantedFields;
    }

    @Override
    public void addElementTags(Short2ShortRBTreeMap tags, Short2ObjectOpenHashMap<String> fields, String tyyppi) {
    	if (tags.get(teksti_kieli) == fin && fields.get(teksti_kieli) != "fin") {
    		return;
    	}
    	
        for (Entry<String> k : fields.short2ObjectEntrySet()) {
            
        	short kk = k.getShortKey();
            String val = k.getValue();
            
            if (val.length() == 0) {
                continue;
            }
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
