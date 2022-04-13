package org.hylly.mtk2garmin;

import java.util.Map;
import java.util.Objects;


class MTKTagHandler implements TagHandler {
    private final short korarvo, syvarvo, nimisuomi, nimiruotsi, teksti, teksti_kieli, kohdeluokka;
    private final short tienro;
    private final short tasosij;
    private final short bridge;
    private final short tunnel;
    private final short yes;

    private final short ele, name, ref, fin;

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
    public void addElementTags(Map<Short, Short> tags, Map<Short, String> fields, String tyyppi, double geomarea) {
        if (tags.containsKey(teksti_kieli) && tags.get(teksti_kieli) == fin && !Objects.equals(fields.get(teksti_kieli), "fin")) {
            return;
        }

        for (Map.Entry<Short, String> k : fields.entrySet()) {
            short kk = k.getKey();
            String val = k.getValue();

            if (val.length() == 0) {
                continue;
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

        if (tyyppi.equals("selite") && "Tuulivoimala".equals(fields.get(teksti))) {
            tags.put(kohdeluokka, this.stringtable.getStringId("45500"));
        }

        if (tyyppi.equals("sahkolinja")) {
            tags.remove(bridge);
        }

    }

}
