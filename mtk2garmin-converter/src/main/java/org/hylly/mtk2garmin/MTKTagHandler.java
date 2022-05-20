package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.Objects;


class MTKTagHandler implements TagHandler {
    private final String kohdeluokka = "kohdeluokka";
    private final String korarvo = "korkeusarvo";
    private final String syvarvo = "syvyysarvo";
    private final String nimisuomi = "nimi_suomi";
    private final String nimiruotsi = "nimi_ruotsi";
    private final String teksti = "teksti";
    private final String teksti_kieli = "teksti_kieli";
    private final String tienro = "tienumero";
    private final String tasosij = "tasosijainti";
    private final String bridge = "bridge";
    private final String tunnel = "tunnel";
    private final String yes = "yes";

    private final String ele = "ele";
    private final String name = "name";
    private final String ref = "ref";
    private final String fin = "fin";


    MTKTagHandler() {
    }

    @Override
    public void addElementTags(Map<String, String> tags, Object2ObjectOpenHashMap<String, String> fields, String tyyppi, double geomarea) {
        if (tags.containsKey(teksti_kieli) && tags.get(teksti_kieli) == fin && !Objects.equals(fields.get(teksti_kieli), "fin")) {
            return;
        }

        fields.forEach((String kk, String val) -> {

            if (val.length() == 0) {
                return;
            }
            if (Objects.equals(kk, korarvo) || Objects.equals(kk, syvarvo)) {
                Double korarvo = Integer.parseInt(val) / 1000.0;
                kk = ele;
                val = String.format("%.1f", korarvo);
            }

            if (Objects.equals(kk, nimisuomi) || Objects.equals(kk, teksti)) {
                kk = name;
            }

            if (Objects.equals(kk, nimiruotsi)) {
                if (!tags.containsKey(name)) {
                    kk = name;
                } else {
                    return;
                }
            }

            if (Objects.equals(kk, tienro)) {
                int tienroVal = Integer.parseInt(val);
                if (tienroVal > 9999) {
                    return;
                }
                kk = ref;
            }

            if (Objects.equals(kk, tasosij)) {
                int sijval = Integer.parseInt(val);
                if (sijval > 0) {
                    tags.put(bridge, yes);
                } else if (sijval < 0) {
                    tags.put(tunnel, yes);
                }
            }

            tags.put(kk, val);
        });

        if (tyyppi.equals("selite") && "Tuulivoimala".equals(fields.get(teksti))) {
            tags.put(kohdeluokka, "45500");
        }

        if (tyyppi.equals("sahkolinja")) {
            tags.remove(bridge);
        }

    }

}
