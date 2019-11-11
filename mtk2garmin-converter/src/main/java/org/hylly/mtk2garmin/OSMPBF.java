package org.hylly.mtk2garmin;

import com.google.protobuf.ByteString;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortMap.Entry;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;
import org.openstreetmap.osmosis.osmbinary.Fileformat;
import org.openstreetmap.osmosis.osmbinary.Osmformat;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.Deflater;

@SuppressWarnings("FieldCanBeLocal")
class OSMPBF {
    private static final Double nano = Double.valueOf(.000000001);
    private final String writingprogram = "mtk2garmin";
    private final Integer granularity = Integer.valueOf(100);
    private final Long lat_offset = Long.valueOf(0L);
    private final Long lon_offset = Long.valueOf(0L);
    private final Integer date_granularity = Integer.valueOf(1000);
    private String ofn;
    private BufferedOutputStream of;
    private DataOutputStream od;

    private PBFBlob createBlob(String blobtype, byte[] data) {
        return new PBFBlob(blobtype, data);
    }

    private Osmformat.HeaderBlock createOSMHeaderBlock(double d, double e,
                                                       double f, double g) {
        Osmformat.HeaderBlock.Builder hbbuilder = Osmformat.HeaderBlock
                .newBuilder();

        hbbuilder.setWritingprogram(this.writingprogram);
        hbbuilder.addRequiredFeatures("OsmSchema-V0.6");
        hbbuilder.addRequiredFeatures("DenseNodes");

        Osmformat.HeaderBBox.Builder bboxbuilder = Osmformat.HeaderBBox
                .newBuilder();

        bboxbuilder.setLeft((long) (d / nano.doubleValue()));
        bboxbuilder.setRight((long) (f / nano.doubleValue()));
        bboxbuilder.setTop((long) (g / nano.doubleValue()));
        bboxbuilder.setBottom((long) (e / nano.doubleValue()));

        hbbuilder.setBbox(bboxbuilder);

        return hbbuilder.build();
    }

    private int topbfcoord(double coord) {
        return (int) ((coord / this.granularity.doubleValue()) / nano.doubleValue());
    }

    @SuppressWarnings("unused")
    private double frompbfcoord(int coord) {
        return nano.doubleValue() * (this.granularity.doubleValue() * (double) coord);
    }

    private Osmformat.StringTable buildStringTable(StringTable stringtable) {
        Osmformat.StringTable.Builder stbuilder = Osmformat.StringTable.newBuilder();
        stringtable.getStringTable()
                .forEach(string -> stbuilder.addS(ByteString.copyFromUtf8(string)));

        System.out.println(stbuilder.getSCount() + " strings in OSMPBF stringtable from MTK2Garmin stringtable " + stringtable.getStringtableSize());
        //System.out.println(Arrays.deepToString(MTKToGarminConverter.stringTable.toArray()));
        //System.out.println(Arrays.deepToString(MTKToGarminConverter.stringTableTranslate.entrySet().toArray()));
        return stbuilder.build();
    }

    private Osmformat.PrimitiveBlock createOSMDataBlock(
            StringTable stringtable,
            Long2ObjectOpenHashMap<Node> nodes, Long2ObjectOpenHashMap<Way> ways,
            Long2ObjectOpenHashMap<Relation> relations) {
        Osmformat.PrimitiveBlock.Builder pbbuilder = Osmformat.PrimitiveBlock
                .newBuilder();
        pbbuilder.setGranularity(this.granularity.intValue());
        pbbuilder.setLatOffset(this.lat_offset.longValue());
        pbbuilder.setLonOffset(this.lon_offset.longValue());
        pbbuilder.setDateGranularity(this.date_granularity.intValue());

        Osmformat.PrimitiveGroup pg = this.createOSMPrimitiveGroup(stringtable, nodes, ways,
                relations);
        pbbuilder.addPrimitivegroup(pg);

        pbbuilder.setStringtable(this.buildStringTable(stringtable));
        return pbbuilder.build();

    }

    private Osmformat.PrimitiveGroup createOSMPrimitiveGroup(
            StringTable stringtable,
            Long2ObjectOpenHashMap<Node> nodes, Long2ObjectOpenHashMap<Way> ways,
            Long2ObjectOpenHashMap<Relation> relations) {
        Osmformat.PrimitiveGroup.Builder pgbuilder = Osmformat.PrimitiveGroup
                .newBuilder();
        if (ways != null)
            pgbuilder.addAllWays(this.buildOSMWays(ways));
        if (relations != null)
            pgbuilder.addAllRelations(this.buildOSMRelations(stringtable, relations));
        if (nodes != null)
            pgbuilder.setDense(this.buildOSMDenseNodes(stringtable, nodes));

        return pgbuilder.build();
    }

    private Osmformat.DenseNodes buildOSMDenseNodes(
            StringTable stringtable,
            final Long2ObjectOpenHashMap<Node> nodes) {

        Osmformat.DenseNodes.Builder dsb = Osmformat.DenseNodes.newBuilder();
        Osmformat.DenseInfo.Builder dib = Osmformat.DenseInfo.newBuilder();

        long lid = 0, llat = 0, llon = 0;

        long pbflat, pbflon;
        //Long[] node_keys_sorted = ArrayUtils.toObject(nodes.keySet().toLongArray());

        Node[] nodes_sorted = new Node[nodes.size()];
        nodes.values().toArray(nodes_sorted);

        Arrays.sort(nodes_sorted, (o1, o2) -> (int) (o1.id - o2.id));

        for (Node n : nodes_sorted) {
            long id = n.getId();
            dsb.addId(id - lid);

            dib.addVersion(1);
            dib.addUid(0);
            dib.addUserSid(0);
            dib.addTimestamp(0);
            dib.addChangeset(0);

            lid = id;

            pbflat = this.topbfcoord(n.getLat());
            pbflon = this.topbfcoord(n.getLon());

            dsb.addLat(pbflat - llat);
            dsb.addLon(pbflon - llon);

            llat = pbflat;
            llon = pbflon;

            Short2ShortRBTreeMap ntags = n.getTags();
            if (ntags != null) {
                for (Entry t : ntags.short2ShortEntrySet()) {
                    if (t.getShortKey() > stringtable.getStringtableSize() || t.getShortValue() > stringtable.getStringtableSize()) {

                        System.out.println("Node key error! " + t.getShortKey() + " or " + t.getShortValue() + " too large");
                    }
                    dsb.addKeysVals(t.getShortKey());
                    dsb.addKeysVals(t.getShortValue());
                }
            }

            dsb.addKeysVals(0);

        }

        //System.out.println("Node ids: " + Arrays.toString(dsb.getKeysValsList().toArray()));
        dsb.setDenseinfo(dib);

        return dsb.build();

    }

    private ArrayList<Osmformat.Way> buildOSMWays(Long2ObjectOpenHashMap<Way> ways) {
        ArrayList<Osmformat.Way> pbfways = new ArrayList<>();
        Osmformat.Info.Builder wib = Osmformat.Info.newBuilder();
        wib.setVersion(1);
        long[] waykeys = ways.keySet().toLongArray();
        Arrays.sort(waykeys);

        for (long wk : waykeys) {
            Way w = ways.get(wk);
            Osmformat.Way.Builder wb = Osmformat.Way.newBuilder();

            wb.setId(w.getId());
            wb.setInfo(wib);

            Short2ShortRBTreeMap wtags = w.getTags();
            for (Entry t : wtags.short2ShortEntrySet()) {
                wb.addKeys(t.getShortKey());
                wb.addVals(t.getShortValue());
            }

            long lref = 0;
            for (int i = 0; i < w.refs.size(); i++) {
                long r = w.refs.getLong(i);

                wb.addRefs(r - lref);
                lref = r;
            }

            pbfways.add(wb.build());
        }

        return pbfways;
    }

    private ArrayList<Osmformat.Relation> buildOSMRelations(
            StringTable stringtable,
            Long2ObjectOpenHashMap<Relation> relations) {
        ArrayList<Osmformat.Relation> pbfrels = new ArrayList<>();

        Osmformat.Info.Builder rib = Osmformat.Info.newBuilder();
        rib.setVersion(1);

        long[] relkeys = relations.keySet().toLongArray();
        Arrays.sort(relkeys);
        for (long rk : relkeys) {
            Relation r = relations.get(rk);
            Osmformat.Relation.Builder rb = Osmformat.Relation.newBuilder();
            rb.setId(r.getId());
            rb.setInfo(rib);

            Short2ShortRBTreeMap rtags = r.getTags();
            for (Entry t : rtags.short2ShortEntrySet()) {
                rb.addKeys(t.getShortKey());
                rb.addVals(t.getShortValue());
            }

            long lmid = 0;
            for (RelationMember m : r.getMembers()) {
                rb.addRolesSid(stringtable.getStringId(m.getRole()));
                rb.addMemids(m.getId() - lmid);
                lmid = m.getId();
                switch (m.getType()) {
                    case "node":
                        rb.addTypes(Osmformat.Relation.MemberType.NODE);
                        break;
                    case "way":
                        rb.addTypes(Osmformat.Relation.MemberType.WAY);
                        break;
                    case "relation":
                        rb.addTypes(Osmformat.Relation.MemberType.RELATION);
                        break;
                }
            }

            pbfrels.add(rb.build());
        }

        return pbfrels;
    }

    void writePBFElements(StringTable stringtable, Boolean close_file,
                          Long2ObjectOpenHashMap<Node> nodes, Long2ObjectOpenHashMap<Way> ways,
                          Long2ObjectOpenHashMap<Relation> relations) throws IOException {
        Osmformat.PrimitiveBlock pb = this
                .createOSMDataBlock(stringtable, nodes, ways, relations);
        PBFBlob data = this.createBlob("OSMData", pb.toByteArray());
        Integer header_sersize = Integer.valueOf(data.header.getSerializedSize());
        od.writeInt(header_sersize.intValue());
        data.header.writeTo(of);
        data.body.writeTo(of);

        of.flush();
        if (close_file.booleanValue()) {
            of.close();
        }
    }

    void writePBFHeaders(String pbfout) throws IOException {
        this.writePBFHeaders(pbfout, 19.0900d, 59.3000d, 31.5900d, 70.1300d);

    }

    void writePBFHeaders(String pbfout, double minx, double miny,
                         double maxx, double maxy) throws IOException {
        assert this.ofn == null;
        assert this.of == null;

        this.ofn = pbfout;
        this.of = new BufferedOutputStream(new FileOutputStream(ofn));
        this.od = new DataOutputStream(this.of);

        Osmformat.HeaderBlock hb = this.createOSMHeaderBlock(minx, miny, maxx,
                maxy);

        PBFBlob head = this.createBlob("OSMHeader", hb.toByteArray());
        Integer header_sersize = Integer.valueOf(head.header.getSerializedSize());
        od.writeInt(header_sersize.intValue());
        head.header.writeTo(of);
        head.body.writeTo(of);

    }

    void closePBF() throws IOException {
        of.close();
    }

    private class PBFBlob {
        private Fileformat.BlobHeader header;
        private Fileformat.Blob body;

        PBFBlob(String blobtype, byte[] payload) {
            int size = payload.length;
            Deflater deflater = new Deflater();
            deflater.setInput(payload);
            deflater.finish();
            byte out[] = new byte[size];
            deflater.deflate(out);

            if (!deflater.finished()) {
                out = Arrays.copyOf(out, size + size / 64 + 16);
                deflater.deflate(out, deflater.getTotalOut(), out.length
                        - deflater.getTotalOut());
                if (!deflater.finished()) {
                    throw new Error("Internal error in compressor");
                }
            }
            ByteString compressed = ByteString.copyFrom(out, 0,
                    deflater.getTotalOut());

            Fileformat.Blob.Builder bodybuilder = Fileformat.Blob.newBuilder();
            bodybuilder.setRawSize(size);
            bodybuilder.setZlibData(compressed);
            deflater.end();

            this.body = bodybuilder.build();

            Fileformat.BlobHeader.Builder headbuilder = Fileformat.BlobHeader
                    .newBuilder();

            headbuilder.setType(blobtype);
            headbuilder.setDatasize(this.body.getSerializedSize());

            this.header = headbuilder.build();

        }
    }

}
