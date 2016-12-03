package org.hylly.mtk2garmin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Vector;
import java.util.zip.GZIPOutputStream;

import org.gdal.ogr.DataSource;
import org.gdal.ogr.Feature;
import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.gdal.osr.osr;

import it.unimi.dsi.fastutil.shorts.Short2ShortMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;
import org.gdal.ogr.*;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.gdal.osr.osr;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPOutputStream;

class MTKToGarminConverter {
    private static final SpatialReference wgs84ref = new SpatialReference();
    private static final Object2ObjectRBTreeMap<String, double[]> gridExtents = new Object2ObjectRBTreeMap<String, double[]>();
    private CoordinateTransformation srctowgs;
    private static final Object2ShortOpenHashMap<String> stringTableTranslate = new Object2ShortOpenHashMap<String>();
    static final ObjectArrayList<String> stringTable = new ObjectArrayList<String>();

    public class Node {
        final long id;
        final double lon;
        final double lat;
        final int cell;
        Short2ShortRBTreeMap nodeTags;
        boolean waypart;
        final long hash;

        public Node(long id, long hash, int cell, double lon, double lat, boolean waypart) {
            this.id = id;
            this.hash = hash;
            this.lon = lon;
            this.lat = lat;
            this.cell = cell;
            this.waypart = waypart;
        }

        public Node(long id, long hash, int cell, double lon, double lat, boolean waypart, Short2ShortRBTreeMap tags) {
            this.id = id;
            this.hash = hash;
            this.lon = lon;
            this.lat = lat;
            this.cell = cell;
            this.waypart = waypart;
            this.nodeTags = tags;
        }

        void addTag(short key, short value) {
            if (nodeTags == null) {
                nodeTags = new Short2ShortRBTreeMap();
            }

            nodeTags.put(key, value);
        }

        long getId() {
            return id;
        }

        double getLon() {
            return lon;
        }

        double getLat() {
            return lat;
        }

        boolean isWaypart() {
            return waypart;
        }

        long getHash() {
            return hash;
        }

        Short2ShortRBTreeMap getTags() {
            return nodeTags;
        }

    }

    class Way {
        long id;

        String getRole() {
            return role;
        }

        void setRole(String role) {
            this.role = role;
        }

        String role = "all";
        LongArrayList refs = new LongArrayList();
        final Short2ShortRBTreeMap tags = new Short2ShortRBTreeMap();

        Way() {

        }

        long getId() {
            return id;
        }

        public Short2ShortRBTreeMap getTags() {
            return tags;
        }
    }

    class RelationMember {
        long id;

        long getId() {
            return id;
        }

        void setId(long id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType() {
            this.type = "way";
        }

        String getRole() {
            return role;
        }

        void setRole(String role) {
            this.role = role;
        }

        String type;
        String role;
    }

    class Relation {
        long getId() {
            return id;
        }

        void setId(long id) {
            this.id = id;
        }

        Short2ShortRBTreeMap getTags() {
            return tags;
        }

        ArrayList<RelationMember> getMembers() {
            return members;
        }

        long id;
        final Short2ShortRBTreeMap tags = new Short2ShortRBTreeMap();
        final ArrayList<RelationMember> members = new ArrayList<RelationMember>();
    }

    private class InitializedDatasource {
        DataSource ds;
        int cell;
    }

    private class GeomHandlerResult {
        final ArrayList<Node> nodes = new ArrayList<Node>();
        final ArrayList<Way> ways = new ArrayList<Way>();
        final ArrayList<Relation> relations = new ArrayList<Relation>();
    }

    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<Node>(50000);
    private final Int2ObjectAVLTreeMap<Long2ObjectAVLTreeMap<Node>> nodepos = new Int2ObjectAVLTreeMap<Long2ObjectAVLTreeMap<Node>>();
    private final Long2ObjectOpenHashMap<Way> ways = new Long2ObjectOpenHashMap<Way>(5000);
    private final Long2ObjectOpenHashMap<Relation> relations = new Long2ObjectOpenHashMap<Relation>(500);

    private static final double COORD_DELTA_X = 62000.0 - 6e3;
    private static final double COORD_DELTA_Y = 6594000.0;
    private static final double COORD_ACC = 2;

    private double minx = Double.POSITIVE_INFINITY, miny = Double.POSITIVE_INFINITY, maxx = Double.NEGATIVE_INFINITY,
            maxy = Double.NEGATIVE_INFINITY;

    private long nodeidcounter = 5000000000L;
    private long wayidcounter = 5000000000L;
    private long relationidcounter = 5000000000L;

    private OSMPBF op;
    private double llx;
    private double lly;
    private double urx;
    private double ury;
    private int max_nodes = 0;
    private int max_ways = 0;
    private int max_relations = 0;

    private static short tyyppi_string_id;

    private MTKToGarminConverter() {
        this.initElements();
        new ArrayList<File>();
    }

    private void initElements() {
        this.nodes.clear();
        this.nodes.trim();
        this.ways.clear();
        this.nodes.trim();
        this.relations.clear();
        this.relations.trim();
    }

    private double[] grid2xy(int grid) {
        return new double[] { (grid >> 16) * 12e3 + COORD_DELTA_X, (grid & 0xFFFF) * 12e3 + COORD_DELTA_Y };
    }

    private GeomHandlerResult handleSingleGeom(Geometry geom) {

        GeomHandlerResult ghr = new GeomHandlerResult();

        if (geom.IsEmpty())
            return ghr;

        boolean ispoint = geom.GetGeometryType() == ogr.wkbPoint || geom.GetGeometryType() == ogr.wkbPoint25D;

        LongArrayList waynodes = new LongArrayList();

        double[][] srcpoints = geom.GetPoints();
        double[][] wgspoints = geom.GetPoints();

        srctowgs.TransformPoints(wgspoints);
        Way w = null;
        long wid;
        if (!ispoint) {
            wid = wayidcounter;
            wayidcounter++;
            w = new Way();
            w.id = wid;
        }
        long phash;
        long nid;
        Node n;

        int pcell;
        for (int i = 0; i < srcpoints.length; i++) {

            phash = hashCoords(srcpoints[i][0], srcpoints[i][1]);
            pcell = xy2grid(srcpoints[i][0], srcpoints[i][1]);

            if (!nodes.containsKey(phash) && (!nodepos.containsKey(pcell) || !nodepos.get(pcell).containsKey(phash))) {
                if (wgspoints[i][0] < this.minx) {
                    this.minx = wgspoints[i][0];
                }
                if (wgspoints[i][1] < this.miny) {
                    this.miny = wgspoints[i][1];
                }

                if (wgspoints[i][0] > this.maxx) {
                    this.maxx = wgspoints[i][0];
                }
                if (wgspoints[i][1] > this.maxy) {
                    this.maxy = wgspoints[i][1];
                }

                if (!nodepos.containsKey(pcell)) {
                    System.out.println("Adding nodepos hashmap: " + pcell);
                    nodepos.put(pcell, new Long2ObjectAVLTreeMap<Node>());
                }

                if (nodepos.get(pcell).containsKey(phash)) {
                    n = nodepos.get(pcell).get(phash);
                } else {
                    nid = nodeidcounter;
                    nodeidcounter++;
                    n = new Node(nid, phash, pcell, wgspoints[i][0], wgspoints[i][1], !ispoint);

                    if (this.nodeNearCellBorder(srcpoints[i])) {
                        nodepos.get(pcell).put(phash, n);
                    }
                }

                nodes.put(phash, n);
                ghr.nodes.add(n);
                if (!ispoint) {
                    waynodes.add(n.getId());
                }
            } else {
                if (nodes.containsKey(phash)) {
                    n = nodes.get(phash);

                    nodepos.get(pcell).put(phash, n);
                } else {
                    n = nodepos.get(pcell).get(phash);
                    nodes.put(phash, n);
                }
                n.waypart = !ispoint;
                ghr.nodes.add(n);
                if (!ispoint) {
                    waynodes.add(n.getId());
                }
            }

        }

        if (w != null) {

            w.refs = waynodes;
            ghr.ways.add(w);
        }

        return ghr;

    }

    private boolean nodeNearCellBorder(double[] srcpoints) {
        double dist = this.calculateMinNodeCellBorderDistance(srcpoints[0], srcpoints[1]);
        return Math.abs(dist) < 50;

    }

    private double calculateMinNodeCellBorderDistance(double x, double y) {
        return Math.min(Math.abs(this.llx - x),
                Math.min(Math.abs(this.lly - y), Math.min(Math.abs(this.urx - x), Math.abs(this.ury - y))));
    }

    private GeomHandlerResult handleMultiGeom(Geometry geom) {

        GeomHandlerResult ighr;
        Geometry igeom;
        GeomHandlerResult ghr = new GeomHandlerResult();

        if (!geom.GetGeometryName().equals("POLYGON")) {
            for (int i = 0; i < geom.GetGeometryCount(); i++) {
                igeom = geom.GetGeometryRef(i);
                ighr = this.handleSingleGeom(igeom);
                ghr.nodes.addAll(ighr.nodes);
                ghr.ways.addAll(ighr.ways);
            }
            return ghr;
        }

        long rid = relationidcounter;
        relationidcounter++;
        Relation r = new Relation();
        r.setId(rid);
        r.tags.put(MTKToGarminConverter.getStringId("type"), MTKToGarminConverter.getStringId("multipolygon"));

        for (int i = 0; i < geom.GetGeometryCount(); i++) {
            igeom = geom.GetGeometryRef(i);

            ighr = this.handleSingleGeom(igeom);
            if (ighr.ways.size() == 0) {
                return new GeomHandlerResult();
            }
            ighr.ways.get(0).setRole((i == 0 ? "outer" : "inner"));

            ghr.nodes.addAll(ighr.nodes);
            ghr.ways.addAll(ighr.ways);

            RelationMember rm = new RelationMember();

            rm.setId(ighr.ways.get(0).getId());
            rm.setType();
            rm.setRole((i == 0 ? "outer" : "inner"));
            r.members.add(rm);
        }

        ghr.relations.add(r);
        return ghr;

    }

    private long calcHash(long a, long b) {
        if (a >= b) {
            return a * a + a + b;
        } else {
            return a + b * b;
        }

    }

    private long hashCoords(double x, double y) {

        return calcHash((long) ((int) (x - COORD_DELTA_X) * COORD_ACC), (long) ((int) (y - COORD_DELTA_Y) * COORD_ACC));

    }

    private boolean handleFeature(String lyrname, ArrayList<Field> fieldMapping, Feature feat,
            FeaturePreprocessI featurePreprocess, TagHandlerI tagHandler) {
        Short2ObjectOpenHashMap<String> fields = new Short2ObjectOpenHashMap<String>();
        Geometry geom;
        for (Field f : fieldMapping) {
            fields.put(MTKToGarminConverter.getStringId(f.getFieldName()),
                    feat.GetFieldAsString(f.getFieldIndex()).intern());
        }

        geom = feat.GetGeometryRef();

        geom = geom.SimplifyPreserveTopology(0.5);

        if (srctowgs == null) {
            SpatialReference sref = geom.GetSpatialReference();
            srctowgs = osr.CreateCoordinateTransformation(sref, wgs84ref);
        }

        int prepstatus = featurePreprocess.preprocessFeature(feat, geom, fields);

        if (prepstatus == 1) {
            return true;
        } else if (prepstatus == 2) {
            return false;
        }

        GeomHandlerResult ghr;

        if (geom.GetGeometryCount() < 2) {
            if (geom.GetGeometryCount() > 0) {
                geom = geom.GetGeometryRef(0);
            }
            ghr = this.handleSingleGeom(geom);
        } else {
            ghr = this.handleMultiGeom(geom);
        }
        geom.delete();
        feat.delete();
        String tyyppi = lyrname.toLowerCase();

        short tyyppi_value_id = MTKToGarminConverter.getStringId(tyyppi);

        for (Node n : ghr.nodes) {

            if (!n.isWaypart()) {
                n.addTag(tyyppi_string_id, tyyppi_value_id);
                tagHandler.addElementTags(n.nodeTags, fields, tyyppi);
            }

            if (!nodes.containsKey(n.getHash())) {
                nodes.put(n.getHash(), n);
            }
        }

        for (Way w : ghr.ways) {
            if (!w.getRole().equals("inner")) {
                w.tags.put(tyyppi_string_id, tyyppi_value_id);
                tagHandler.addElementTags(w.tags, fields, tyyppi);
            }
            if (!ways.containsKey(w.getId())) {
                ways.put(w.getId(), w);
            }
        }

        for (Relation r : ghr.relations) {

            r.tags.put(tyyppi_string_id, tyyppi_value_id);
            tagHandler.addElementTags(r.tags, fields, tyyppi);
            if (!relations.containsKey(r.getId()))
                relations.put(r.getId(), r);
        }

        return true;

    }

    static void printTags(Short2ShortRBTreeMap tags) {
        for (Entry t : tags.short2ShortEntrySet()) {
            System.out.println(getStringById(t.getShortKey()) + " => " + getStringById(t.getShortValue()));
        }
    }

    static short getStringId(String stringKey) {
        if (!MTKToGarminConverter.stringTableTranslate.containsKey(stringKey)) {
            short newIndex = (short)MTKToGarminConverter.stringTable.size();
            MTKToGarminConverter.stringTableTranslate.put(stringKey, newIndex);
            MTKToGarminConverter.stringTable.add(newIndex, stringKey);
            return newIndex;
        } else {
            return MTKToGarminConverter.stringTableTranslate.getShort(stringKey);
        }

    }

    static String getStringById(int id) {
        return MTKToGarminConverter.stringTable.get(id);
    }

    private void writeOSMXMLTags(OutputStream fos, Short2ShortRBTreeMap nodeTags) throws IOException {
        if (nodeTags.size() == 0)
            return;

        for (Entry k : nodeTags.short2ShortEntrySet()) {
            fos.write(
                    String.format("\t\t<tag k=\"%s\" v=\"%s\" />\n", MTKToGarminConverter.getStringById(k.getShortKey()),
                            MTKToGarminConverter.getStringById(k.getShortValue())).getBytes());
        }
    }

    @SuppressWarnings("unused")
    private void writePlainOSMXML(String ofn) throws IOException {
        BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(ofn));

        this.writeOSMXML(fos);
    }

    @SuppressWarnings("unused")
    private void writeGzOSMXML(String ofn) throws IOException {
        GZIPOutputStream fos = new GZIPOutputStream(new FileOutputStream(ofn));
        this.writeOSMXML(fos);
    }

    private void writeOSMXML(OutputStream fos) throws IOException {

        fos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes());
        fos.write("<osm version=\"0.6\" generator=\"mtk2garmin 0.0.1\">\n".getBytes());

        fos.write(String
                .format("\t<bounds minlon=\"%f\" minlat=\"%f\" maxlon=\"%f\" maxlat=\"%f\"/>\n", minx, miny, maxx, maxy)
                .getBytes());

        ObjectArrayList<Long2ObjectMap.Entry<Node>> node_keys_sorted = new ObjectArrayList<Long2ObjectMap.Entry<Node>>();

        for (Long2ObjectMap.Entry<Node> nk : nodes.long2ObjectEntrySet()) {
            node_keys_sorted.add(nk);
        }

        Collections.sort(node_keys_sorted, new Comparator<Long2ObjectMap.Entry<Node>>() {
            public int compare(Long2ObjectMap.Entry<Node> k1, Long2ObjectMap.Entry<Node> k2) {
                return (int) (k1.getValue().getId() - k2.getValue().getId());
            }
        });

        for (Long2ObjectMap.Entry<Node> nk : node_keys_sorted) {
            Node n = nk.getValue();
            fos.write(String.format("\t\t<node id=\"%d\" visible=\"true\" version=\"1\" lat=\"%f\" lon=\"%f\"",
                    n.getId(), n.getLat(), n.getLon()).getBytes());

            if (n.nodeTags.size() > 0) {

                fos.write(">\n".getBytes());
                this.writeOSMXMLTags(fos, n.nodeTags);
                fos.write("\t</node>\n".getBytes());

            } else {
                fos.write(" />\n".getBytes());
            }
        }
        long[] waykeys = ways.keySet().toLongArray();
        Arrays.sort(waykeys);

        for (long wk : waykeys) {
            Way w = ways.get(wk);
            fos.write(String.format("\t<way id=\"%d\" visible=\"true\" version=\"1\">\n", w.getId()).getBytes());

            for (int i = 0; i < w.refs.size(); i++) {
                fos.write(String.format("\t\t<nd ref=\"%d\" />\n", w.refs.getLong(i)).getBytes());
            }
            this.writeOSMXMLTags(fos, w.tags);
            fos.write("\t</way>\n".getBytes());
        }
        long[] relkeys = relations.keySet().toLongArray();
        Arrays.sort(relkeys);

        for (long rk : relkeys) {
            Relation r = relations.get(rk);
            fos.write(String.format("\t<relation id=\"%d\" version=\"1\" visible=\"true\">\n", r.getId()).getBytes());
            for (RelationMember m : r.members) {
                fos.write(String.format("\t\t<member type=\"%s\" ref=\"%d\" role=\"%s\"/>\n", m.getType(), m.getId(),
                        m.getRole()).getBytes());
            }
            this.writeOSMXMLTags(fos, r.tags);
            fos.write("\t</relation>\n".getBytes());
        }
        fos.write("</osm>".getBytes());

        fos.close();
    }

    private void startWritingOSMPBF(String ofn) throws IOException {
        op = new OSMPBF();
        op.writePBFHeaders(ofn);
    }

    private void writeOSMPBFElements() throws IOException {

        op.writePBFElements(false, nodes, null, null);
        op.writePBFElements(false, null, ways, null);
        op.writePBFElements(false, null, null, relations);

        // this.initElements();
    }

    private void closeOSMPBFFile() throws IOException {
        op.closePBF();
    }

    @SuppressWarnings("unused")
    public void writeOSMPBF(String ofn) throws IOException {
        op = new OSMPBF();
        op.writePBFHeaders(ofn, minx, miny, maxx, maxy);
        op.writePBFElements(true, nodes, null, null);
        op.writePBFElements(true, null, ways, null);
        op.writePBFElements(true, null, null, relations);
    }

    private double[] extendExtent(double[] ext1, double[] ext2) {

        return new double[] { (ext1[0] < ext2[0] ? ext1[0] : ext2[0]), (ext1[1] > ext2[1] ? ext1[1] : ext2[1]),
                (ext1[2] < ext2[2] ? ext1[2] : ext2[2]), (ext1[3] > ext2[3] ? ext1[3] : ext2[3]), };
    }

    private void clearNodeCache(int cell) {
        double[] ll = this.grid2xy(cell);

        int[] cellids = this.nodepos.keySet().toIntArray();

        for (int cellid : cellids) {
            if (!this.nodepos.containsKey(cellid)) {
                continue;
            }

            double[] cell_ll = this.grid2xy(cellid);
            if (cell_ll[0] <= ll[0] - 24e3 && cell_ll[1] <= ll[1] - 24e3) {
                int numdelids = this.nodepos.get(cellid).size();
                this.nodepos.remove(cellid);
                System.out.println("nodepos removed: " + cellid + ", " + numdelids + " ids by " + cell);
            }
        }

    }

    private InitializedDatasource startReadingOGRFile(String fn) {
        System.out.println("Initializing file " + fn);
        InitializedDatasource is = new InitializedDatasource();
        DataSource ds = ogr.Open(fn, false);
        is.ds = ds;
        if (ds == null) {
            System.out.println("Reading file failed");
            return is;
        }

        Layer lyr;
        double[] extent = new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY };

        for (int i = 0; i < ds.GetLayerCount(); i++) {
            lyr = ds.GetLayer(i);
            extent = this.extendExtent(extent, lyr.GetExtent());
        }

        int cell = this.extent2grid(extent);
        is.cell = cell;

        double[] ll = this.grid2xy(cell);

        double[] ur = new double[] { ll[0] + 12e3, ll[1] + 12e3 };

        this.setCellBBOX(ll, ur);

        if (!nodepos.containsKey(cell)) {
            nodepos.put(cell, new Long2ObjectAVLTreeMap<Node>());
        }

        return is;
    }

    private void readOGRsource(String fn, FeaturePreprocessI featurePreprocess, TagHandlerI tagHandler,
            boolean doClearNodeCache, double[] filterExtent) {
        InitializedDatasource is = startReadingOGRFile(fn);
        DataSource ds = is.ds;

        if (doClearNodeCache) {
            this.clearNodeCache(is.cell);
        }

        if (ds == null) {
            return;
        }
        Layer lyr;
        String fname;

        String attributefilter = featurePreprocess.getAttributeFilterString();

        HashSet<String> ignored_fields = new HashSet<String>();
        FieldDefn fdefn;
        layerloop: for (int i = 0; i < ds.GetLayerCount(); i++) {
            lyr = ds.GetLayer(i);
            Vector<String> ignoredFields = new Vector<String>();

            if (!featurePreprocess.isWantedLayer(lyr.GetName().toLowerCase())) {
                continue;
            }

            if (filterExtent != null) {
                System.out.println("Filter rect: " + Arrays.toString(filterExtent));
                lyr.SetSpatialFilterRect(filterExtent[0], filterExtent[2], filterExtent[1], filterExtent[3]);
            }

            if (attributefilter != null) {
                lyr.SetAttributeFilter(attributefilter);
            }

            FeatureDefn lyrdefn = lyr.GetLayerDefn();
            ArrayList<Field> fieldMapping = new ArrayList<Field>();
            for (int i1 = 0; i1 < lyrdefn.GetFieldCount(); i1++) {
                fdefn = lyrdefn.GetFieldDefn(i1);
                fname = fdefn.GetName();

                if (!tagHandler.getWantedFields().contains(fname)) {
                    ignoredFields.add(fname);

                } else {
                    fieldMapping.add(new Field(fname, fdefn.GetFieldType(), i1));
                }
            }


            ignored_fields.addAll(ignoredFields);
            if (lyr.TestCapability(ogr.OLCIgnoreFields) && ignoredFields.size() > 0) {
                lyr.SetIgnoredFields(ignoredFields);
            }

            for (Feature feat = lyr.GetNextFeature(); feat != null; feat = lyr.GetNextFeature()) {

                if (!this.handleFeature(lyr.GetName(), fieldMapping, feat, featurePreprocess, tagHandler)) {
                    System.out.println("BREAK");
                    break layerloop;
                }

            }
            lyr.delete();

        }

        ds.delete();
        System.out.println("Ignored fields: " + Arrays.toString(ignored_fields.toArray()));
        // return is.extent;

    }

    private int xy2grid(double x, double y) {
        int gx = (int) Math.floor((x - COORD_DELTA_X) / 12e3);
        int gy = (int) Math.floor((y - COORD_DELTA_Y) / 12e3);

        return gx << 16 | gy;

    }

    private int extent2grid(double[] extent) {
        return xy2grid(extent[1] - 5, extent[3] - 5);
    }

    private void setCellBBOX(double[] ll, double[] ur) {
        this.llx = ll[0];
        this.lly = ll[1];
        this.urx = ur[0];
        this.ury = ur[1];

    }

    private void printCounts() {
        System.out.println(nodes.size() + " nodes " + ways.size() + " ways " + relations.size() + " relations");
    }

    private void trackCounts() {
        if (nodes.size() > max_nodes) max_nodes = nodes.size();
        if (ways.size() > max_ways) max_ways = ways.size();
        if (relations.size() > max_relations) max_relations = relations.size();
        
        System.out.println("max_nodes " + max_nodes +  ", max_ways " + max_ways + ", max_relations " + max_relations);
        
    }

    private static String getDataFile(String file) throws IOException {
        final String DATA_DIR = "/Users/sdahlbacka/git/mtk2garmin/geodata";

        Path fullPath = Paths.get(DATA_DIR, file);
        String path = fullPath.toString();
        if (!Files.exists(fullPath)) { throw new FileNotFoundException("Data file '" + path + "' not found.");}
        return path;
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Starting conversion");
        ogr.UseExceptions();
        Locale.setDefault(new Locale("en", "US"));
        wgs84ref.SetWellKnownGeogCS("WGS84");

        ogr.RegisterAll();
        // wget --no-check-certificate -O grid.gml
        // "https://tiedostopalvelu.maanmittauslaitos.fi/geoserver/ows/?service=wfs&request=GetFeature&typeName=Grid"

        DataSource gridds = ogr.Open("C:\\geodata\\12x12grid.shp");
        Layer lyr = gridds.GetLayer(0);

        lyr.SetAttributeFilter("gridSize = '12x12'");

        double[] extent = new double[4];
        for (Feature feat = lyr.GetNextFeature(); feat != null; feat = lyr.GetNextFeature()) {
            Geometry geom = feat.GetGeometryRef();

            geom.GetEnvelope(extent);
            gridExtents.put(feat.GetFieldAsString(1), extent.clone());
            feat.delete();
        }
        lyr.delete();
        gridds.delete();

        HashMap<String, ArrayList<File>> areas = new HashMap<String, ArrayList<File>>();
        File srcdir = new File(getDataFile("mtkgml"));
        for (File g1 : srcdir.listFiles()) {
            for (File g2 : g1.listFiles()) {
                for (File g3 : g2.listFiles()) {
                    if (!g3.getName().endsWith(".zip")) {
                        continue;
                    }

                    String area = g3.getName().substring(0, 4);

                    if (!areas.containsKey(area)) {
                        areas.put(area, new ArrayList<File>());
                    }
                    areas.get(area).add(g3);
                }
            }
        }

        Object[] areassorted = areas.keySet().toArray();
        Arrays.sort(areassorted);
        MTKToGarminConverter mtk2g = new MTKToGarminConverter();

        ShapeRetkeilyTagHandler retkeilyTagHandler = new ShapeRetkeilyTagHandler();
        ShapeSyvyysTagHandler syvyysTagHandler = new ShapeSyvyysTagHandler();
        MMLTagHandler tagHandlerMML;

        MMLFeaturePreprocess featurePreprocessMML = new MMLFeaturePreprocess();
        ShapeFeaturePreprocess shapePreprocessor = new ShapeFeaturePreprocess();
        double[] mml_extent;

        int currentArea = 0;
        for (Object area : areassorted) {
            System.out.printf("Processing area (%s) %d/%d%n", area, ++currentArea, areassorted.length);
            ArrayList<File> files = areas.get(area);
            String[] filenames = new String[files.size()];
            int i = 0;
            for (File f : files) {

                filenames[i] = f.getAbsolutePath();
                i++;
            }

            Arrays.sort(filenames);

            for (String fn : filenames) {

                String cell = fn.substring(fn.lastIndexOf("\\") + 1, fn.lastIndexOf("\\") + 7);
                String cell = fn.substring(fn.lastIndexOf(File.separator) + 1, fn.lastIndexOf(File.separator) + 7);

                MTKToGarminConverter.stringTable.clear();
                stringTable.trim(1000);
                MTKToGarminConverter.stringTableTranslate.clear();
                stringTableTranslate.trim(1000);
                stringTable.add("");

                tyyppi_string_id = MTKToGarminConverter.getStringId("tyyppi");
                tagHandlerMML = new MMLTagHandler();

                Path outputDir = Paths.get("suomi");
                if (!Files.exists(outputDir)) {
                    Files.createDirectory(outputDir);
                }
                mtk2g.startWritingOSMPBF(
                        Paths.get("suomi", String.format("%s.osm.pbf", cell)).toString());

                System.out.println(fn + " (" + cell + ")");

                mml_extent = gridExtents.get(cell);
                long st;

                st = System.nanoTime();
                mtk2g.readOGRsource("/vsizip/" + fn, featurePreprocessMML, tagHandlerMML, true, null);
                System.out.println("mtk read " + (System.nanoTime() - st) / 1000000000.0);
                mtk2g.printCounts();
                System.out.println(Arrays.toString(mml_extent));

                st = System.nanoTime();
                mtk2g.readOGRsource("/vsizip/" + getDataFile("syvyyskayrat.zip") + File.separator + "syvyyskayra_v.shp"  , shapePreprocessor,
                        syvyysTagHandler, false, mml_extent);
                mtk2g.printCounts();
                System.out.println("Syvyyskayrat read " + (System.nanoTime() - st) / 1000000000.0);

                st = System.nanoTime();
                mtk2g.readOGRsource("/vsizip/" + getDataFile("syvyyspiste_p.zip") + File.separator + "syvyyspiste_p.shp", shapePreprocessor,
                        syvyysTagHandler, false, mml_extent);
                mtk2g.printCounts();
                System.out.println("Syvyyspisteet read " + (System.nanoTime() - st) / 1000000000.0);

                st = System.nanoTime();
                mtk2g.readOGRsource(
                        "/vsizip/" + getDataFile("kesaretkeilyreitit.zip") + File.separator + "kesaretkeilyreititLine.shp",
                        shapePreprocessor, retkeilyTagHandler, false, mml_extent);
                mtk2g.printCounts();
                System.out.println("Kesaretkeilyreitit read " + (System.nanoTime() - st) / 1000000000.0);

                st = System.nanoTime();
                mtk2g.readOGRsource("/vsizip/" + getDataFile("ulkoilureitit.zip") + File.separator + "ulkoilureititLine.shp",
                        shapePreprocessor, retkeilyTagHandler, false, mml_extent);
                mtk2g.printCounts();
                System.out.println("Ulkoilureitit read " + (System.nanoTime() - st) / 1000000000.0);

                st = System.nanoTime();
                mtk2g.readOGRsource("/vsizip/" + getDataFile("luontopolut.zip") + File.separator + "luontopolutLine.shp",
                        shapePreprocessor, retkeilyTagHandler, false, mml_extent);
                mtk2g.printCounts();
                System.out.println("Luontopolut read " + (System.nanoTime() - st) / 1000000000.0);

                st = System.nanoTime();
                mtk2g.readOGRsource("/vsizip/" + getDataFile("point_dump.zip") + File.separator + "point_dumpPoint.shp",
                        shapePreprocessor, retkeilyTagHandler, false, mml_extent);
                mtk2g.printCounts();
                System.out.println("Point_dump read " + (System.nanoTime() - st) / 1000000000.0);

                /*
                 * st = System.nanoTime(); mtk2g.readOGRsource(
                 * "/vsizip/C:\\geodata\\retkikartta\\hirvialueet.zip\\hirvialueet.shp",
                 * shapePreprocessor, retkeilyTagHandler, false, mml_extent);
                 * mtk2g.printCounts(); System.out.println("Hirvialueet read " +
                 * (System.nanoTime() - st) / 1000000000.0);
                 * 
                 * st = System.nanoTime(); mtk2g.readOGRsource(
                 * "/vsizip/C:\\geodata\\retkikartta\\pienriista.zip\\pienriista.shp",
                 * shapePreprocessor, retkeilyTagHandler, false, mml_extent);
                 * mtk2g.printCounts(); System.out.println("Pienriista read " +
                 * (System.nanoTime() - st) / 1000000000.0);
                 */
                
                
                st = System.nanoTime();
                mtk2g.writeOSMPBFElements();
                mtk2g.closeOSMPBFFile();
                mtk2g.trackCounts();
                mtk2g.initElements();
                System.out.println("pbf " + (System.nanoTime() - st) / 1000000000.0);
                
            }
            
            

            if (mtk2g.nodepos.size() > 0) {
                System.out.println(area + " done!");
                System.out.println(mtk2g.nodepos.size() + " cells");
                long numids = 0;
                for (Int2ObjectMap.Entry<Long2ObjectAVLTreeMap<Node>> ntree : mtk2g.nodepos.int2ObjectEntrySet()) {
                    numids += ntree.getValue().size();
                }
                System.out.println(numids + " ids in cache");
            }
        }
        System.exit(1);

    }



}
