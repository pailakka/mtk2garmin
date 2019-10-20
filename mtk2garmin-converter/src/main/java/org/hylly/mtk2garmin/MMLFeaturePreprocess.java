package org.hylly.mtk2garmin;

class MMLFeaturePreprocess implements FeaturePreprocessI {
    public String getAttributeFilterString() {
        return "kohdeluokka NOT IN (30211,30212,42200,42111,42112,42110,42151,42152,42111,42112,42110,42151,42152,42150,42121,42122,42120,42131,42132,42130,42161,42162,42160,42200,42141,42142,42140)";
    }

}
