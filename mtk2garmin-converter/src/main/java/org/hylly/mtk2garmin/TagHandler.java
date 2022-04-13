package org.hylly.mtk2garmin;


import java.util.Map;

interface TagHandler {
    void addElementTags(Map<Short, Short> tags, Map<Short, String> fields, String tyyppi, double geomarea);
}
