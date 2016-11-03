package org.hylly.mtk2garmin;

import org.gdal.ogr.Feature;
import org.gdal.ogr.Geometry;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;


class MMLFeaturePreprocess implements FeaturePreprocessI{
	public String getAttributeFilterString() {
		return "kohdeluokka NOT IN (30211,30212,42200,42111,42112,42110,42151,42152,42111,42112,42110,42151,42152,42150,42121,42122,42120,42131,42132,42130,42161,42162,42160,42200,42141,42142,42140)";
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
