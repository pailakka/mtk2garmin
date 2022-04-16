package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

import java.util.Map;
import java.util.Objects;


class MTKTagHandler implements TagHandler {
    private final int korarvo, syvarvo, nimisuomi, nimiruotsi, teksti, teksti_kieli, kohdeluokka;
    private final int tienro;
    private final int tasosij;
    private final int bridge;
    private final int tunnel;
    private final int yes;

    private final int ele, name, ref, fin;

    private final StringTable stringtable;


    MTKTagHandler(StringTable stringtable) {
        kohdeluokka = stringtable.getStringId("kohdeluokka");
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
    public void addElementTags(Map<Integer, Integer> tags, Int2ObjectArrayMap<String> fields, String tyyppi, double geomarea) {
        if (tags.containsKey(teksti_kieli) && tags.get(teksti_kieli) == fin && !Objects.equals(fields.get(teksti_kieli), "fin")) {
            return;
        }

        fields.forEach((Integer kk, String val) -> {

            if (val.length() == 0) {
                return;
            }
            if (kk == korarvo || kk == syvarvo) {
                Double korarvo = Integer.parseInt(val) / 1000.0;
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
                    return;
                }
            }

            if (kk == tienro) {
                int tienroVal = Integer.parseInt(val);
                if (tienroVal > 9999) {
                    return;
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
        });

        if (tyyppi.equals("selite") && "Tuulivoimala".equals(fields.get(teksti))) {
            tags.put(kohdeluokka, this.stringtable.getStringId("45500"));
        }

        if (tyyppi.equals("sahkolinja")) {
            tags.remove(bridge);
        }

    }

}
