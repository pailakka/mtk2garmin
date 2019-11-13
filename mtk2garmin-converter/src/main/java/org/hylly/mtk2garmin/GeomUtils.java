package org.hylly.mtk2garmin;

import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Layer;

public class GeomUtils {
    private double llx;
    private double lly;
    private double urx;
    private double ury;


    private final double COORD_DELTA_X = 62000.0 - 6e3;
    private final double COORD_DELTA_Y = 6594000.0;

    int calculateDatasourceCell(DataSource ds) {
        Layer lyr;
        double[] extent = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY};

        for (int i = 0; i < ds.GetLayerCount(); i++) {
            lyr = ds.GetLayer(i);
            extent = this.extendExtent(extent, lyr.GetExtent());
        }

        int cell = this.extent2grid(extent);

        double[] ll = this.grid2xy(cell);

        double[] ur = new double[]{ll[0] + 12e3, ll[1] + 12e3};

        this.setCellBBOX(ll, ur);

//        if (!nodepos.containsKey(cell)) {
//            nodepos.put(cell, Long2ObjectMaps.synchronize(new Long2ObjectAVLTreeMap<>()));
//        }

        return cell;
    }

    private int extent2grid(double[] extent) {
        return xy2grid(extent[1] - 5, extent[3] - 5);
    }

    private void setCellBBOX(double[] ll, double[] ur) {
        this.llx = ll[0];
        this.lly = ll[1];
        this.urx = ur[0];
        this.ury = ur[1];

    }


    private double[] grid2xy(int grid) {
        return new double[]{(grid >> 16) * 12e3 + COORD_DELTA_X, (grid & 0xFFFF) * 12e3 + COORD_DELTA_Y};
    }


    private int xy2grid(double x, double y) {
        int gx = (int) Math.floor((x - COORD_DELTA_X) / 12e3);
        int gy = (int) Math.floor((y - COORD_DELTA_Y) / 12e3);

        return gx << 16 | gy;

    }

    private double[] extendExtent(double[] ext1, double[] ext2) {

        return new double[]{
                Math.min(ext1[0], ext2[0]), Math.max(ext1[1], ext2[1]),
                Math.min(ext1[2], ext2[2]), Math.max(ext1[3], ext2[3])};
    }
}
