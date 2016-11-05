package org.hylly.mtk2garmin;

import org.gdal.ogr.Feature;
import org.gdal.ogr.Geometry;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;

interface FeaturePreprocessI {
	int preprocessFeature(Feature feat,Geometry geom, Short2ObjectOpenHashMap<String> fields);

	boolean isWantedLayer(String lowerCase);

	String getAttributeFilterString();
}
