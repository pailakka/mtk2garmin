#!/bin/bash
mkdir -p inputdata
wget --retry 5 --retry-delay 30 -O inputdata/grid.zip "https://tiedostopalvelu.maanmittauslaitos.fi/geoserver/karttalehti/ows?service=wfs&request=GetFeature&typeName=Grid&outputFormat=shape-zip&cql_filter=gridSize=%2712x12%27"
wget --retry 5 --retry-delay 30 -O inputdata/finland-latest.osm.pbf "http://download.geofabrik.de/europe/finland-latest.osm.pbf"
wget --retry 5 --retry-delay 30 -O inputdata/syvyyskayra_v.zip "https://extranet.liikennevirasto.fi/inspirepalvelu/rajoitettu/wfs?request=GetFeature&typeName=syvyyskayra_v&outputFormat=shape-zip"
wget --retry 5 --retry-delay 30 -O inputdata/syvyyspiste_p.zip "https://extranet.liikennevirasto.fi/inspirepalvelu/rajoitettu/wfs?request=GetFeature&typeName=syvyyspiste_p&outputFormat=shape-zip"
wget --retry 5 --retry-delay 30 -O inputdata/kesaretkeilyreitit.zip "http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:kesaretkeilyreitit&outputFormat=shape-zip"
wget --retry 5 --retry-delay 30 -O inputdata/ulkoilureitit.zip "http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:ulkoilureitit&outputFormat=shape-zip"
wget --retry 5 --retry-delay 30 -O inputdata/luontopolut.zip "http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:luontopolut&outputFormat=shape-zip"
wget --retry 5 --retry-delay 30 -O inputdata/point_dump.zip "http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/?request=GetFeature&typeName=retkikartta_euref:point_dump&outputFormat=shape-zip"
