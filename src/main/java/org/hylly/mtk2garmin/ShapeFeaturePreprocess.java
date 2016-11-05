package org.hylly.mtk2garmin;

import org.gdal.ogr.Feature;
import org.gdal.ogr.Geometry;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;

class ShapeFeaturePreprocess implements FeaturePreprocessI{
	@Override
	public int preprocessFeature(Feature feat, Geometry geom, Short2ObjectOpenHashMap<String> fields) {
        return 0;
	}

	@Override
	public boolean isWantedLayer(String lowerCase) {
		return true;
	}

	@Override
	public String getAttributeFilterString() {
		return null;
	}

}
