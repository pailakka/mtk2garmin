package org.hylly.mtk2garmin;

import org.gdal.ogr.*;
import com.typesafe.config.Config;

import java.util.logging.Logger;
import java.util.stream.Stream;

class CachedAdditionalDataSources {
    private Logger logger = Logger.getLogger(CachedAdditionalDataSources.class.getName());
    private final Driver memoryd;

    private final DataSource syvyyskayrat;
    private final DataSource syvyyspisteet;

    CachedAdditionalDataSources(Config conf) {
        this.memoryd = ogr.GetDriverByName("memory");

        syvyyskayrat = createMemoryCacheFromOGRFile(conf.getString("syvyyskayrat"));
        syvyyspisteet = createMemoryCacheFromOGRFile(conf.getString("syvyyspisteet"));
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

    Stream<DataSource> getDatasources() {
        return Stream.of(
                syvyyskayrat,
                syvyyspisteet
        );
    }
}
