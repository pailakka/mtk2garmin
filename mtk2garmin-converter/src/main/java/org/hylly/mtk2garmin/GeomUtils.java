package org.hylly.mtk2garmin;

import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.gdal.osr.osr;

import java.util.HashMap;
import java.util.Map;

class GeomUtils {
    private final double COORD_DELTA_X = 62000.0 - 6e3;
    private final double COORD_DELTA_Y = 6594000.0;

    private final SpatialReference wgs84ref = new SpatialReference();

    private Map<String, CoordinateTransformation> coordinateTransformationCache = new HashMap<>();

    GeomUtils() {
        this.wgs84ref.SetWellKnownGeogCS("WGS84");
    }

    int xy2grid(double x, double y) {
        int gx = (int) ((x - COORD_DELTA_X) / 48e3);
        int gy = (int) ((y - COORD_DELTA_Y) / 24e3);

        return gx << 16 | gy;

    }

    double[] extendExtent(double[] ext1, double[] ext2) {

        return new double[]{
                Math.min(ext1[0], ext2[0]), Math.max(ext1[1], ext2[1]),
                Math.min(ext1[2], ext2[2]), Math.max(ext1[3], ext2[3])};
    }

    CoordinateTransformation getTransformationToWGS84(String proj4str) {
        if (coordinateTransformationCache.containsKey(proj4str)) {
            return coordinateTransformationCache.get(proj4str);
        }

        SpatialReference from = new SpatialReference();
        from.ImportFromProj4(proj4str);

        CoordinateTransformation transform = osr.CreateCoordinateTransformation(from, wgs84ref);

        coordinateTransformationCache.put(proj4str, transform);
        return transform;
    }

    long hashCoords(double x, double y) {
        double COORD_ACC = 2;
        return calcHash((long) ((int) (x - COORD_DELTA_X) * COORD_ACC), (long) ((int) (y - COORD_DELTA_Y) * COORD_ACC));

    }

    private long calcHash(long a, long b) {
        if (a >= b) {
            return a * a + a + b;
        } else {
            return a + b * b;
        }

    }

    boolean pointInside(double[] searchBBox, double[] search) {
        return search[0] >= searchBBox[0] && search[0] <= searchBBox[1] &&
                search[1] >= searchBBox[2] && search[1] <= searchBBox[3];
    }
}
