package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Objects;

import static java.util.Arrays.asList;


class MMLTagHandler implements TagHandlerI {
    private final ObjectOpenHashSet<String> wantedFields;

    private final int korarvo;
    private final int syvarvo;
    private final int nimisuomi;
    private final int nimiruotsi;
    private final int teksti;
    private final int teksti_kieli;
    private final int kohdeluokka;
    private final int tienro;
    private final int tasosij;
    private final int bridge;
    private final int tunnel;
    private final int yes;

    private final int ele, name, ref, fin;

    private final StringTable stringtable;


    MMLTagHandler(StringTable stringtable) {
        wantedFields = new ObjectOpenHashSet<>(asList("nimi_ruotsi", "nimi_suomi", "kohdeluokka", "yksisuuntaisuus", "tienumero", "korkeusarvo", "tasosijainti", "syvyysarvo", "valmiusaste", "paallyste", "teksti", "teksti_kieli"));

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
    public ObjectOpenHashSet<String> getWantedFields() {
        return this.wantedFields;
    }

    @Override
    public void addElementTags(Int2IntMap tags, Int2ObjectMap<String> fields, String tyyppi, double geomarea) {
        if (tags.get(teksti_kieli) == fin && !Objects.equals(fields.get(teksti_kieli), "fin")) {
            return;
        }

        for (Entry<String> k : fields.int2ObjectEntrySet()) {

            int kk = k.getIntKey();
            String val = k.getValue();

            if (val.isEmpty()) {
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
