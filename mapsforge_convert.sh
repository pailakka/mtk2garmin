export JAVACMD_OPTIONS="-Xmx30G"
osmosis/bin/osmosis --rbf file=all_osm.osm.pbf workers=4 --mapfile-writer file=all.map bbox=59.4507573,19.0714057,70.1120744,31.6133108 simplification-max-zoom=13 simplification-factor=3 threads=2 tag-conf-file=mapsforge_peruskartta/mml_tag-mapping_tidy.xml type=hd comment="(c) NLS, Metsahallitus, Liikennevirasto, OpenStreetMap contributors 2017"
