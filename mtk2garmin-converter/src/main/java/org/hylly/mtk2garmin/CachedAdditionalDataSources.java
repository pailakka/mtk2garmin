package org.hylly.mtk2garmin;

import org.gdal.ogr.DataSource;
import org.gdal.ogr.Driver;
import org.gdal.ogr.ogr;
import com.typesafe.config.Config;

import java.util.logging.Logger;

public class CachedAdditionalDataSources {
    private final Driver memoryd;
    private final GeomUtils geomUtils;

    Logger logger = Logger.getLogger(CachedAdditionalDataSources.class.getName());

    private final InitializedDatasource syvyyskayrat;
    private final InitializedDatasource syvyyspisteet;
    private final InitializedDatasource kesaretkeily;
    private final InitializedDatasource ulkoilureitit;
    private final InitializedDatasource luontopolut;
    private final InitializedDatasource metsapoints;

    CachedAdditionalDataSources(Config conf, GeomUtils geomUtils) {
        this.memoryd = ogr.GetDriverByName("memory");
        this.geomUtils = geomUtils;

        syvyyskayrat = createMemoryCacheFromOGRFile(conf.getString("syvyyskayrat"));
        syvyyspisteet = createMemoryCacheFromOGRFile(conf.getString("syvyyspisteet"));

        kesaretkeily = createMemoryCacheFromOGRFile(conf.getString("retkikartta") + "/kesaretkeilyreitit.gml");
        ulkoilureitit = createMemoryCacheFromOGRFile(conf.getString("retkikartta") + "/ulkoilureitit.gml");
        luontopolut = createMemoryCacheFromOGRFile(conf.getString("retkikartta") + "/luontopolut.gml");
        metsapoints = createMemoryCacheFromOGRFile(conf.getString("retkikartta") + "/point_dump.gml");
    }



    private InitializedDatasource createMemoryCacheFromOGRFile(String fn) {
        logger.info("Copying " + fn + " to in memory cache");

        InitializedDatasource is = new InitializedDatasource();

        DataSource ds = ogr.Open(fn, false);


        if (ds == null) {
            logger.severe("Reading file " + fn + " failed");
            System.exit(1);
        }

        DataSource mds = memoryd.CopyDataSource(ds, "mem_" + fn);

        ds.delete();
        logger.info(fn + " copied to cache. " + mds.GetLayerCount() + " layers");
        is.ds = mds;

        is.cell = geomUtils.calculateDatasourceCell(is.ds);
        return is;
    }

}
