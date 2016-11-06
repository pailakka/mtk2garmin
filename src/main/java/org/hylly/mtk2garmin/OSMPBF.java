package org.hylly.mtk2garmin;

import com.google.protobuf.ByteString;
import crosby.binary.Fileformat;
import crosby.binary.Osmformat;
import it.unimi.dsi.fastutil.shorts.Short2ShortMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;

import org.hylly.mtk2garmin.MTKToGarminConverter.Node;
import org.hylly.mtk2garmin.MTKToGarminConverter.Relation;
import org.hylly.mtk2garmin.MTKToGarminConverter.RelationMember;
import org.hylly.mtk2garmin.MTKToGarminConverter.Way;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.Deflater;

@SuppressWarnings("FieldCanBeLocal")
class OSMPBF {
    private final String writingprogram = "mtk2garmin";
    private final Integer granularity = 100;
    private final Long lat_offset = 0L;
    private final Long lon_offset = 0L;

    private final Integer date_granularity = 1000;

    private static final Double nano = .000000001;

    private String ofn;
    private BufferedOutputStream of;
    private DataOutputStream od;

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

    private int getString(String ostr) {
        return MTKToGarminConverter.getStringId(ostr);
    }

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

        bboxbuilder.setLeft((long) (d / nano));
        bboxbuilder.setRight((long) (f / nano));
        bboxbuilder.setTop((long) (g / nano));
        bboxbuilder.setBottom((long) (e / nano));

        hbbuilder.setBbox(bboxbuilder);

        return hbbuilder.build();
    }

    private int topbfcoord(double coord) {
        return (int) ((coord / this.granularity) / nano);
    }

    @SuppressWarnings("unused")
    private double frompbfcoord(int coord) {
        return nano * (this.granularity * (double) coord);
    }

    private Osmformat.StringTable buildStringTable() {
        Osmformat.StringTable.Builder stbuilder = Osmformat.StringTable.newBuilder();
        for (int i = 0; i < MTKToGarminConverter.stringTable.size(); i++) {
            stbuilder.addS(ByteString.copyFromUtf8(MTKToGarminConverter.stringTable.get(i)));
        }
        System.out.println(stbuilder.getSCount() + " strings in OSMPBF stringtable from MTK2Garmin stringtable " + MTKToGarminConverter.stringTable.size());
        //System.out.println(Arrays.deepToString(MTKToGarminConverter.stringTable.toArray()));
        //System.out.println(Arrays.deepToString(MTKToGarminConverter.stringTableTranslate.entrySet().toArray()));
        return stbuilder.build();
    }

    private Osmformat.PrimitiveBlock createOSMDataBlock(
            Long2ObjectOpenHashMap<Node> nodes, Long2ObjectOpenHashMap<Way> ways,
            Long2ObjectOpenHashMap<Relation> relations) {
        Osmformat.PrimitiveBlock.Builder pbbuilder = Osmformat.PrimitiveBlock
                .newBuilder();
        pbbuilder.setGranularity(this.granularity);
        pbbuilder.setLatOffset(this.lat_offset);
        pbbuilder.setLonOffset(this.lon_offset);
        pbbuilder.setDateGranularity(this.date_granularity);

        Osmformat.PrimitiveGroup pg = this.createOSMPrimitiveGroup(nodes, ways,
                relations);
        pbbuilder.addPrimitivegroup(pg);

        pbbuilder.setStringtable(this.buildStringTable());
        return pbbuilder.build();

    }

    private Osmformat.PrimitiveGroup createOSMPrimitiveGroup(
            Long2ObjectOpenHashMap<Node> nodes, Long2ObjectOpenHashMap<Way> ways,
            Long2ObjectOpenHashMap<Relation> relations) {
        Osmformat.PrimitiveGroup.Builder pgbuilder = Osmformat.PrimitiveGroup
                .newBuilder();
        if (ways != null)
            pgbuilder.addAllWays(this.buildOSMWays(ways));
        if (relations != null)
            pgbuilder.addAllRelations(this.buildOSMRelations(relations));
        if (nodes != null)
            pgbuilder.setDense(this.buildOSMDenseNodes(nodes));

        return pgbuilder.build();
    }

    private Osmformat.DenseNodes buildOSMDenseNodes(
            final Long2ObjectOpenHashMap<Node> nodes) {

        Osmformat.DenseNodes.Builder dsb = Osmformat.DenseNodes.newBuilder();
        Osmformat.DenseInfo.Builder dib = Osmformat.DenseInfo.newBuilder();

        long lid = 0, llat = 0, llon = 0;

        long pbflat, pbflon;
        //Long[] node_keys_sorted = ArrayUtils.toObject(nodes.keySet().toLongArray());

        Node[] nodes_sorted = new Node[nodes.size()];
        nodes.values().toArray(nodes_sorted);

        Arrays.sort(nodes_sorted, new Comparator<Node>() {
            @Override
            public int compare(Node o1, Node o2) {
                return (int) (o1.id - o2.id);
            }
        });

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
                    if (t.getShortKey() > MTKToGarminConverter.stringTable.size() || t.getShortValue() > MTKToGarminConverter.stringTable.size()) {

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
        ArrayList<Osmformat.Way> pbfways = new ArrayList<Osmformat.Way>();
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
            Long2ObjectOpenHashMap<Relation> relations) {
        ArrayList<Osmformat.Relation> pbfrels = new ArrayList<Osmformat.Relation>();

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
                rb.addRolesSid(this.getString(m.getRole()));
                rb.addMemids(m.getId() - lmid);
                lmid = m.getId();
                if (m.getType().equals("node")) rb.addTypes(Osmformat.Relation.MemberType.NODE);
                else if (m.getType().equals("way")) rb.addTypes(Osmformat.Relation.MemberType.WAY);
                else if (m.getType().equals("relation")) rb.addTypes(Osmformat.Relation.MemberType.RELATION);
            }

            pbfrels.add(rb.build());
        }

        return pbfrels;
    }

    void writePBFElements(Boolean close_file,
                          Long2ObjectOpenHashMap<Node> nodes, Long2ObjectOpenHashMap<Way> ways,
                          Long2ObjectOpenHashMap<Relation> relations) throws IOException {
        Osmformat.PrimitiveBlock pb = this
                .createOSMDataBlock(nodes, ways, relations);
        PBFBlob data = this.createBlob("OSMData", pb.toByteArray());
        Integer header_sersize = data.header.getSerializedSize();
        od.writeInt(header_sersize);
        data.header.writeTo(of);
        data.body.writeTo(of);

        of.flush();
        if (close_file) {
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
        Integer header_sersize = head.header.getSerializedSize();
        od.writeInt(header_sersize);
        head.header.writeTo(of);
        head.body.writeTo(of);

    }

    void closePBF() throws IOException {
        of.close();
    }

}
