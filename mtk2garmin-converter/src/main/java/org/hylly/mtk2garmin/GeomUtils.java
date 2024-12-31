package org.hylly.mtk2garmin;

import org.gdal.ogr.GeomTransformer;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import static org.gdal.osr.osrConstants.OAMS_TRADITIONAL_GIS_ORDER;

class GeomUtils {
    private final double COORD_DELTA_X = 62000.0 - 6e3;
    private final double COORD_DELTA_Y = 6594000.0;

    private final SpatialReference sphericmercref = new SpatialReference();
    public final GeomTransformer spherictowgs;

    GeomUtils() {
        SpatialReference wgs84ref = new SpatialReference();
        wgs84ref.SetWellKnownGeogCS("WGS84");
        wgs84ref.SetAxisMappingStrategy(OAMS_TRADITIONAL_GIS_ORDER);

        this.sphericmercref.ImportFromProj4("+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m +nadgrids=@null +wktext +no_defs +type=crs");
        this.sphericmercref.SetAxisMappingStrategy(OAMS_TRADITIONAL_GIS_ORDER);

        this.spherictowgs = new GeomTransformer(new CoordinateTransformation(sphericmercref, wgs84ref));

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

    public GeomTransformer getTransformationToSphereMercator(String proj4str) {
        SpatialReference from = new SpatialReference();
        from.ImportFromProj4(proj4str);
        from.SetAxisMappingStrategy(OAMS_TRADITIONAL_GIS_ORDER);

        CoordinateTransformation transform = new CoordinateTransformation(from, sphericmercref);

        return new GeomTransformer(transform);
    }
}
