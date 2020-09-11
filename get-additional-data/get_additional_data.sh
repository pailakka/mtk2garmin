#!/bin/bash
set -e

mkdir -p /additional-data
curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/grid.zip "https://tiedostopalvelu.maanmittauslaitos.fi/geoserver/karttalehti/ows?service=wfs&request=GetFeature&typeName=Grid&outputFormat=shape-zip&cql_filter=gridSize=%2712x12%27"
curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/finland-latest.osm.pbf "http://download.geofabrik.de/europe/finland-latest.osm.pbf"
ogr2ogr --config OGR_WFS_PAGE_SIZE 4000 --debug on -nln syvyyskayra_v -f "ESRI shapefile" /additional-data/syvyyskayra_v.shp WFS:https://julkinen.traficom.fi/inspirepalvelu/rajoitettu/wfs?typeName=rajoitettu:DepthContour_L
ogr2ogr --config OGR_WFS_PAGE_SIZE 4000 --debug on -nln syvyyspiste_p -f "ESRI shapefile" /additional-data/syvyyspiste_p.shp WFS:https://julkinen.traficom.fi/inspirepalvelu/rajoitettu/wfs?typeName=rajoitettu:Sounding_P
#curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/kesaretkeilyreitit.gml "https://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:kesaretkeilyreitit&outputFormat=GML2&service=wfs"
#curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/ulkoilureitit.gml "https://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:ulkoilureitit&outputFormat=GML2&service=wfs"
#curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/luontopolut.gml "https://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:luontopolut&outputFormat=GML2&service=wfs"
#curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/point_dump.gml "https://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:point_dump&outputFormat=GML2&service=wfs"

ogrinfo -so /additional-data/syvyyspiste_p.shp syvyyspiste_p
ogrinfo -so /additional-data/syvyyskayra_v.shp syvyyskayra_v
#ogrinfo -so /additional-data/kesaretkeilyreitit.gml kesaretkeilyreitit
#ogrinfo -so /additional-data/ulkoilureitit.gml ulkoilureitit
#ogrinfo -so /additional-data/luontopolut.gml luontopolut
#ogrinfo -so /additional-data/point_dump.gml point_dump