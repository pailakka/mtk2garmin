package org.hylly.mtk2garmin;

import org.gdal.ogr.Feature;
import org.gdal.ogr.Geometry;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public interface FeaturePreprocessI {
	public int preprocessFeature(Feature feat,Geometry geom, Int2ObjectOpenHashMap<String> fields);

	public boolean isWantedLayer(String lowerCase);

	public String getAttributeFilterString();
}
