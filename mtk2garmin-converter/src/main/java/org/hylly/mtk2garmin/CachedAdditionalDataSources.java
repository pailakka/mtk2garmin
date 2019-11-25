package org.hylly.mtk2garmin;

import org.gdal.ogr.*;
import com.typesafe.config.Config;

import java.util.logging.Logger;
import java.util.stream.Stream;

public class CachedAdditionalDataSources {
    private final Driver memoryd;
    private final GeomUtils geomUtils;
    private final Driver spatialited;

    Logger logger = Logger.getLogger(CachedAdditionalDataSources.class.getName());

    private final DataSource syvyyskayrat;
    private final DataSource syvyyspisteet;
    private final DataSource kesaretkeily;
    private final DataSource ulkoilureitit;
    private final DataSource luontopolut;
    private final DataSource metsapoints;

    CachedAdditionalDataSources(Config conf, GeomUtils geomUtils) {
        this.memoryd = ogr.GetDriverByName("memory");
        this.spatialited = ogr.GetDriverByName("sqlite");
        this.geomUtils = geomUtils;

        syvyyskayrat = createMemoryCacheFromOGRFile(conf.getString("syvyyskayrat"));
        syvyyspisteet = createMemoryCacheFromOGRFile(conf.getString("syvyyspisteet"));

        kesaretkeily = createMemoryCacheFromOGRFile(conf.getString("retkikartta") + "/kesaretkeilyreitit.gml");
        ulkoilureitit = createMemoryCacheFromOGRFile(conf.getString("retkikartta") + "/ulkoilureitit.gml");
        luontopolut = createMemoryCacheFromOGRFile(conf.getString("retkikartta") + "/luontopolut.gml");
        metsapoints = createMemoryCacheFromOGRFile(conf.getString("retkikartta") + "/point_dump.gml");
    }


    private DataSource createMemoryCacheFromOGRFile(String fn) {
        logger.info("Copying " + fn + " to in memory cache");

        DataSource ds = ogr.Open(fn, false);
        if (ds == null) {
            logger.severe("Reading file " + fn + " failed");
            System.exit(1);
        }

        DataSource mds = memoryd.CopyDataSource(ds, "mem_" + ds.GetLayer(0).GetName());

        ds.delete();
        logger.info(fn + " copied to cache. " + mds.GetLayerCount() + " layers");
        return mds;
    }

    DataSource getNewDatasource(String ds) {
        DataSource nds = ogr.Open(ds); //memoryd.CopyDataSource(ds, "mem_" + ds.GetLayer(0).GetName() +  UUID.randomUUID().toString());
        return nds;
    }

    public Stream<DataSource> getDatasources() {
        return Stream.of(
                syvyyskayrat,
                syvyyspisteet,
                kesaretkeily,
                ulkoilureitit,
                luontopolut,
                metsapoints
        );
    }
}
