package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap.Entry;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

import java.util.Objects;

import static java.util.Arrays.asList;


class MMLTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;

    private final short korarvo, syvarvo, nimisuomi, nimiruotsi, teksti, teksti_kieli;
    private final short tienro, tasosij, bridge, tunnel, yes;

    private final short ele, name, ref, fin;

    private final StringTable stringtable;


    MMLTagHandler(StringTable stringtable) {
        wantedFields = new ObjectOpenHashSet<>(asList("nimi_ruotsi", "nimi_suomi", "kohdeluokka", "yksisuuntaisuus", "tienumero", "korkeusarvo", "tasosijainti", "syvyysarvo", "valmiusaste", "paallyste", "teksti", "teksti_kieli"));

        korarvo = stringtable.getStringId("korkeusarvo");
        syvarvo = stringtable.getStringId("syvyysarvo");
        nimisuomi = stringtable.getStringId("nimi_suomi");
        nimiruotsi = stringtable.getStringId("nimi_ruotsi");
        teksti = stringtable.getStringId("teksti");
        teksti_kieli = stringtable.getStringId("teksti_kieli");
        tienro = stringtable.getStringId("tienumero");
        tasosij = stringtable.getStringId("tasosijainti");
        bridge = stringtable.getStringId("bridge");
        tunnel = stringtable.getStringId("tunnel");
        yes = stringtable.getStringId("yes");

        ele = stringtable.getStringId("ele");
        name = stringtable.getStringId("name");
        ref = stringtable.getStringId("ref");
        fin = stringtable.getStringId("fin");

        this.stringtable = stringtable;

    }

    @Override
    public ObjectOpenHashSet<String> getWantedFields() {
        return this.wantedFields;
    }

    @Override
    public void addElementTags(Short2ShortRBTreeMap tags, Short2ObjectOpenHashMap<String> fields, String tyyppi) {
        if (tags.get(teksti_kieli) == fin && !Objects.equals(fields.get(teksti_kieli), "fin")) {
            return;
        }

        for (Entry<String> k : fields.short2ObjectEntrySet()) {

            short kk = k.getShortKey();
            String val = k.getValue();

            if (val.length() == 0) {
                continue;
            }
            if (kk == korarvo || kk == syvarvo) {
                Double korarvo = Double.valueOf(Integer.parseInt(val) / 1000.0);
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
                int tienroVal = Integer.parseInt(val);
                if (tienroVal > 9999) {
                    continue;
                }
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

            tags.put(kk, this.stringtable.getStringId(val));
        }

        if (tyyppi.equals("sahkolinja")) {
            tags.remove(bridge);
        }

    }

}
