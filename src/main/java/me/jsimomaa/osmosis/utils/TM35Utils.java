package me.jsimomaa.osmosis.utils;

import fi.nls.aluejako.karttalehtijako.utm_karttalehti;

/**
 * Utility class for TM35 coordinates
 * 
 * @author jsimomaa
 *
 */
public class TM35Utils {

    /**
     * Method for reverse geocoding coordinates into TM35 map sheets. Example
     * usage: <br>
     * <br>
     * <code>TM35Utils.reverseGeocode(6678000, 368000, TM35Scale.SCALE_5000);</code>
     * <br>
     * <br>
     * Should return <code>L4132E1</code>
     *
     * @param lat
     *            coordinate
     * @param lon
     *            coordinate
     * @param scale
     *            of the sheet
     * @return String identifying the map sheet (e.g. L4132E1)
     * @throws IllegalSearchCriteriaException
     */
    public static String reverseGeocode(double lat, double lon, TM35Scale scale) {
        double[] pt = new double[] { lat, lon }; // E, N (EPSG:3067)

        utm_karttalehti lehti = new utm_karttalehti();
        lehti = lehti.pisteessa(pt, scale.getIntVal());
        return lehti.lehtinumero();
    }

    public enum TM35Scale { // pitää olla jokin näistä: 100000,50000,25000,20000,10000,5000
        SCALE_100000(100000),
        SCALE_50000(50000),
        SCALE_25000(25000),
        SCALE_20000(20000),
        SCALE_10000(10000),
        SCALE_5000(5000);

        private int scale;

        TM35Scale(int scale) {
            this.scale = scale;
        }

        public int getIntVal() {
            return scale;
        }
    }
}