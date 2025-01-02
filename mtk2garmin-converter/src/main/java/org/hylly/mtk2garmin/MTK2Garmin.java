package org.hylly.mtk2garmin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class MTK2Garmin {
    static Logger logger = Logger.getLogger(MTKToGarminConverter.class.getName());

    public static void main(String[] args) throws IOException {
        File configFile = getConfigFile(args);
        if (configFile == null) {
            return;
        }
        MTKToGarminConverter converter = new MTKToGarminConverter(configFile);

        converter.doConvert();
    }

    private static File getConfigFile(String[] args) {
        if (args.length != 1) {
            System.out.println("USAGE: mtk2garmin.jar <configfile>");
            return null;
        }
        File configFile = new File(args[0]);
        assert configFile.exists();
        return configFile;

    }
}
