package org.hylly.mtk2garmin;

import org.gdal.ogr.Feature;
import org.gdal.ogr.Geometry;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;


class MMLFeaturePreprocess implements FeaturePreprocessI{
    MMLFeaturePreprocess() {
		System.out.println("MMLFeaturePreprocess init");
	}
	
	public String getAttributeFilterString() {
		return null;
	}
	boolean hasWantedClass(int wclass) {
		return true;
	}
	
	public boolean isWantedLayer(String wtype) {
		return true;
	}
	
	@Override
	public int preprocessFeature(Feature feat, Geometry geom, Int2ObjectOpenHashMap<String> fields) {
		return 0;
	}
	
}
