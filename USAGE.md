# mtk2garmin process & usage
## Acquiring required datasets and tools
* osmconvert64 (https://wiki.openstreetmap.org/wiki/Osmconvert) and osmfilter (http://wiki.openstreetmap.org/wiki/Osmfilter) needs to be found from PATH
* mgkmap and splitter (http://www.mkgmap.org.uk/) needs to be found from PATH
* Osmosis for Mapsforge conversion (https://wiki.openstreetmap.org/wiki/Osmosis)
* wget
* Python 2 (http://www.python.org)
  * requests (`pip install requests`)
  * urllib3 (`pip install urllib3`)
* Finnish national grid dataset (only 12km x 12km grid needed)
  * Can be obtained from https://tiedostopalvelu.maanmittauslaitos.fi/geoserver/ows/?service=wfs (License unknown, assumed CC 4.0 like other NLS data)
  * Layer "Grid"
* National topographic database from NLS (about 9.5 GB)
  * Loaded using helper script `update_data.py`. Simply running `python update_data.py <api_key>` creates necessary directory structure and creates 8 .bat files to load the whole (or just changed grid tiles) topographic database using wget (assumes wget can be found from PATH) 
  * Requires personal API-key to ATOM-feed (http://www.maanmittauslaitos.fi/aineistot-palvelut/latauspalvelut/avoimien-aineistojen-tiedostopalvelu/muutostietopalvelu)
  * Whole process is based on NLS specific GML format because it is the only format containing all of the data and attributes.
* OpenStreetMap
  * Only features with specific tags are used
  * Load Finland OSM PBF dump from http://download.geofabrik.de/europe/finland-latest.osm.pbf
  * `join_osm_mtk.bat` creates new o5m file with desired filters applied, and actually merges filtered o5m file to output from the actual topographic database conversion process
* Finnish Transport Agency (Nautical chart data & depth data)
  * Depth data from https://extranet.liikennevirasto.fi/inspirepalvelu/rajoitettu/wfs
    * Whole dataset can be loaded from wfs and is used by the process as a zipped shape
  * Other nautical chart data from https://extranet.liikennevirasto.fi/inspirepalvelu/avoin/wfs (not yet used)
* Retkikartta  / Metsähallitus
  * Data from http://www.retkikartta.fi/wfs/a9e9a1840ee69e32d59af86dd1ffeb44/? (unofficial, might change in future. No official source because of reasons)
    * Currently following layers are used:
      * kesaretkeilyreititLine
      * ulkoilureititLine
      * luontopolut
      * point_dumpPoint
      * hirvialueet (not included in styles / tag mapping)
      * pienriista (not included in styles / tag mapping)
  * National/official hunting areas are not included in the maps because valid concerns about data validity and timeliness.

## MTKtoGarminConverter
This is the actual process doing the conversion from national topographic database GML-files to OSM PBF format and
combining most of the former datasets with the topograhic data. It does some necessary data transformations and simplifications on the way (defined in `*TagHandler`classes).
All of the work is done in ETRS-TM35FIN (3067) coordinate system and coordinates are transformed to WGS84 only for PBF output.
Process calculates spatial hash for each nodes and combines/connects (or at least tries) lines on grid cell edges with somewhat varying success.
Because of this combination/connection process, the whole conversion process must be completed as a whole as node ids are generated on the fly.
Process is especially memory optimized and runs consistently well within 2 GB of memory, there is much room for more optimization and for example threaded processing is completely possible.

Process assumes that all data is located in `c:\geodata`. The process works one grid cell at a time and loads auxillary data based on grid cell bounding box and removes unnecessary data from memory after processing. Technically every OGR compatible format should be good for the auxillary data.
`C:\geodata\mtkgml\` directory is read using 2 level directory structure (`eg. C:\geodata\mtkgml\L4\L44\*.zip`). Processing of the whole topographic database is not necessary, if the directory structure matches. 

This step takes about 8 hours with a (slow) SSD, Intel Core i7 920 and 12 GB RAM (and output to HD).

## Combining OSM PBF files
* Running `merge_suomi.py` creates `merge_suomi.bat` from OSM PBF files outputted to `suomi/` directory. It creates PBF files for each of 3 levels (`eg. L444.osm.pbf, L44.osm.pbf, L4.osm.pbf`) for debugging and final `all.osm.pbf`using `osmconvert64`
* Running `join_osm_mtk.bat` merges filtered OSM data and `all.osm.pbf` to `all_osm.osm.pbf`containing all of the necessary map data (and much extra data)

## Creating Garmin map
1. Run splitter `java -Xmx4G -jar splitter.jar --output-dir=splitted suomi/all_osm.osm.pbf > splitter.log
2. Copy contents of `mkgmap_mtk2garmin.args` to right place in `splitted/template.args`
3. Run mkgmap `java -jar mmkgmap.jar -c splitted/template.args suomi\all_osm.osm.pbf M0000320_4.typ` (`M0000320_4.typ` generated from `m0000320_4.txt`, don't ask how, I actually edit the typ with typwiz (http://pinns.co.uk/osm/typwiz4.html))

This step is suprisingly fast

Resulting gmapsupp.img is good to be transferred to GPS or it can be splitted to Basecamp/Mapsource installable version with GMapTool (http://www.gmaptool.eu/en/content/gmaptool) or included nsis installer could be used (but I dislike)

## Creating Mapsforge map in AWS EC2
Consult also https://github.com/mapsforge/mapsforge/blob/master/docs/Getting-Started-Map-Writer.md

This steps requires large amounts of memory so EC2 instance is used, r3.2xlarge seems to have enough memory. Tag mapping affects memory usage significantly. 

Styles should be defined at least on some level before the conversion proces because conversion includes only features defined in tag mapping.

A helper script should be used before the conversion process:
* `tidy_tag_mapping.py` tries to ensure that tag mapping has everything in peruskartta.xml and vice versa. It also changes zoom-appear values according to peruskartta.xml

The easy way is to upload necessary data to S3 and run `convert_mapsforge.sh` on the instance with sudo. Results are automatically transferred back to S3 is process is successful.

The actual process is as follows:

1. Download osmosis and install Mapsforge writer plugin (https://github.com/mapsforge/mapsforge/blob/master/docs/Getting-Started-Map-Writer.md)
2. Run Osmosis with writer plugin and tag-mapping and copyright notices with `osmosis --rbf all_osm.osm.pbf --mapfile-writer file=all.map bbox=59.4507573,19.0714057,70.1120744,31.6133108 tag-conf-file=mml_tag-mapping_tidy.xml type=hd comment="(c) NLS, Metsahallitus, Liikennevirasto, OpenStreetMap contributors 2016"`

And out comes all.map containing all of the data in Mapsforge format

