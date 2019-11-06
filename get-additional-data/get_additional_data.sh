#!/bin/bash
set -e

mkdir -p /inputdata
curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/grid.zip "https://tiedostopalvelu.maanmittauslaitos.fi/geoserver/karttalehti/ows?service=wfs&request=GetFeature&typeName=Grid&outputFormat=shape-zip&cql_filter=gridSize=%2712x12%27"
curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/finland-latest.osm.pbf "http://download.geofabrik.de/europe/finland-latest.osm.pbf"
ogr2ogr --config OGR_WFS_PAGE_SIZE 2000 --debug on -nln syvyyskayra_v -f "ESRI shapefile" /additional-data/syvyyskayra_v.shp WFS:https://extranet.liikennevirasto.fi/inspirepalvelu/rajoitettu/wfs?typeName=rajoitettu:syvyyskayra_v
ogr2ogr --config OGR_WFS_PAGE_SIZE 2000 --debug on -nln syvyyspiste_p -f "ESRI shapefile" /additional-data/syvyyspiste_p.shp WFS:https://extranet.liikennevirasto.fi/inspirepalvelu/rajoitettu/wfs?typeName=rajoitettu:syvyyspiste_p
curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/kesaretkeilyreitit.gml "http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:kesaretkeilyreitit&outputFormat=GML2&service=wfs"
curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/ulkoilureitit.gml "http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:ulkoilureitit&outputFormat=GML2&service=wfs"
curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/luontopolut.gml "http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:luontopolut&outputFormat=GML2&service=wfs"
curl --fail --verbose --retry 5 --retry-delay 30 -o /additional-data/point_dump.gml "http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:point_dump&outputFormat=GML2&service=wfs"
