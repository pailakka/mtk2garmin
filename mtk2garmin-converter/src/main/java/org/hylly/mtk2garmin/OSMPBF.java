package org.hylly.mtk2garmin;

import com.google.protobuf.ByteString;
import org.openstreetmap.osmosis.osmbinary.Fileformat;
import org.openstreetmap.osmosis.osmbinary.Osmformat;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.Deflater;

@SuppressWarnings("FieldCanBeLocal")
class OSMPBF {
    private static final Double NANO = .000000001;
    private static final String WRITING_PROGRAM = "mtk2garmin";
    private static final Integer GRANULARITY = 100;
    private static final Long LAT_OFFSET = 0L;
    private static final Long LON_OFFSET = 0L;
    private static final Integer DATE_GRANULARITY = 1000;
    private final File outFile;
    private BufferedOutputStream of;
    private final DataOutputStream od;

    OSMPBF(File outFile) {
        this.outFile = outFile;
        try {
            this.of = new BufferedOutputStream(new FileOutputStream(this.outFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        this.od = new DataOutputStream(this.of);
    }

    private PBFBlob createBlob(String blobtype, byte[] data) {
        return new PBFBlob(blobtype, data);
    }

    private Osmformat.HeaderBlock createOSMHeaderBlock(double d, double e,
                                                       double f, double g) {
        Osmformat.HeaderBlock.Builder hbbuilder = Osmformat.HeaderBlock
                .newBuilder();

        hbbuilder.setWritingprogram(WRITING_PROGRAM);
        hbbuilder.addRequiredFeatures("OsmSchema-V0.6");
        hbbuilder.addRequiredFeatures("DenseNodes");

        Osmformat.HeaderBBox.Builder bboxbuilder = Osmformat.HeaderBBox
                .newBuilder();

        bboxbuilder.setLeft((long) (d / NANO));
        bboxbuilder.setRight((long) (f / NANO));
        bboxbuilder.setTop((long) (g / NANO));
        bboxbuilder.setBottom((long) (e / NANO));

        hbbuilder.setBbox(bboxbuilder);

        return hbbuilder.build();
    }

    private int topbfcoord(double coord) {
        return (int) ((coord / GRANULARITY.doubleValue()) / NANO);
    }

    @SuppressWarnings("unused")
    private double frompbfcoord(int coord) {
        return NANO * (GRANULARITY.doubleValue() * (double) coord);
    }

    private Osmformat.StringTable buildStringTable(StringTable stringtable) {
        Osmformat.StringTable.Builder stbuilder = Osmformat.StringTable.newBuilder();
        stringtable.getStringTable()
                .forEach(string -> stbuilder.addS(ByteString.copyFromUtf8(string)));

        return stbuilder.build();
    }

    private Osmformat.PrimitiveBlock createOSMDataBlock(
            StringTable stringtable,
            Stream<Node> nodes, Stream<Way> ways,
            Stream<Relation> relations) {
        Osmformat.PrimitiveBlock.Builder pbbuilder = Osmformat.PrimitiveBlock
                .newBuilder();
        pbbuilder.setGranularity(GRANULARITY);
        pbbuilder.setLatOffset(LAT_OFFSET);
        pbbuilder.setLonOffset(LON_OFFSET);
        pbbuilder.setDateGranularity(DATE_GRANULARITY);

        Osmformat.PrimitiveGroup pg = this.createOSMPrimitiveGroup(stringtable, nodes, ways,
                relations);
        pbbuilder.addPrimitivegroup(pg);

        pbbuilder.setStringtable(this.buildStringTable(stringtable));
        return pbbuilder.build();

    }

    private Osmformat.PrimitiveGroup createOSMPrimitiveGroup(
            StringTable stringtable,
            Stream<Node> nodes, Stream<Way> ways,
            Stream<Relation> relations) {
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
            final Stream<Node> nodes) {

        Osmformat.DenseNodes.Builder dsb = Osmformat.DenseNodes.newBuilder();
        Osmformat.DenseInfo.Builder dib = Osmformat.DenseInfo.newBuilder();

        AtomicLong lid = new AtomicLong();
        AtomicLong llat = new AtomicLong();
        AtomicLong llon = new AtomicLong();

        nodes
                .sorted(Comparator.comparingLong(Node::getId))
                .forEach(n -> {
                    long id = n.getId();
                    dsb.addId(id - lid.get());

                    dib.addVersion(1);
                    dib.addUid(0);
                    dib.addUserSid(0);
                    dib.addTimestamp(0);
                    dib.addChangeset(0);

                    lid.set(id);
                    long pbflat = this.topbfcoord(n.getLat());
                    long pbflon = this.topbfcoord(n.getLon());

                    dsb.addLat(pbflat - llat.get());
                    dsb.addLon(pbflon - llon.get());

                    llat.set(pbflat);
                    llon.set(pbflon);

                    Map<Short, Short> ntags = n.getTags();
                    if (ntags != null) {
                        for (Map.Entry<Short, Short> t : ntags.entrySet()) {
                            short k = t.getKey();
                            if (k > stringtable.getStringtableSize() || t.getValue() > stringtable.getStringtableSize()) {
                                System.out.println("Node key error! " + k + " or " + t.getValue() + " too large");
                            }
                            dsb.addKeysVals(k);
                            dsb.addKeysVals(t.getValue());
                        }
                    }

                    dsb.addKeysVals(0);

                });

        dsb.setDenseinfo(dib);

        return dsb.build();

    }

    private ArrayList<Osmformat.Way> buildOSMWays(Stream<Way> ways) {
        ArrayList<Osmformat.Way> pbfways = new ArrayList<>();
        Osmformat.Info.Builder wib = Osmformat.Info.newBuilder();
        wib.setVersion(1);

        ways
                .sorted(Comparator.comparingLong(Way::getId))
                .forEach(w -> {
//            Way w = ways.get(wk);
                    Osmformat.Way.Builder wb = Osmformat.Way.newBuilder();

                    wb.setId(w.getId());
                    wb.setInfo(wib);
                    Map<Short, Short> wtags = w.getTags();
                    for (Map.Entry<Short, Short> t : wtags.entrySet()) {
                        wb.addKeys(t.getKey());
                        wb.addVals(t.getValue());
                    }

                    long lref = 0;
                    for (int i = 0; i < w.refs.size(); i++) {
                        long r = w.refs.get(i);
                        wb.addRefs(r - lref);
                        lref = r;
                    }

                    pbfways.add(wb.build());
                });

        return pbfways;
    }

    private ArrayList<Osmformat.Relation> buildOSMRelations(
            StringTable stringtable,
            Stream<Relation> relations) {
        ArrayList<Osmformat.Relation> pbfrels = new ArrayList<>();

        Osmformat.Info.Builder rib = Osmformat.Info.newBuilder();
        rib.setVersion(1);

        relations
                .sorted(Comparator.comparingLong(Relation::getId))
                .forEach(r -> {
                    Osmformat.Relation.Builder rb = Osmformat.Relation.newBuilder();
                    rb.setId(r.getId());
                    rb.setInfo(rib);

                    Map<Short, Short> rtags = r.getTags();
                    for (Map.Entry<Short, Short> t : rtags.entrySet()) {
                        rb.addKeys(t.getKey());
                        rb.addVals(t.getValue());
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
                });

        return pbfrels;
    }

    void writePBFElements(StringTable stringtable,
                          Stream<Node> nodes, Stream<Way> ways,
                          Stream<Relation> relations) throws IOException {
        Osmformat.PrimitiveBlock pb = this
                .createOSMDataBlock(stringtable, nodes, ways, relations);
        PBFBlob data = this.createBlob("OSMData", pb.toByteArray());
        int header_sersize = data.header.getSerializedSize();
        od.writeInt(header_sersize);
        data.header.writeTo(of);
        data.body.writeTo(of);

        of.flush();
    }

    void writePBFHeaders() throws IOException {
        this.writePBFHeaders(19.0900d, 59.3000d, 31.5900d, 70.1300d);

    }

    private void writePBFHeaders(double minx, double miny,
                                 double maxx, double maxy) throws IOException {
        Osmformat.HeaderBlock hb = this.createOSMHeaderBlock(minx, miny, maxx,
                maxy);

        PBFBlob head = this.createBlob("OSMHeader", hb.toByteArray());
        int header_sersize = head.header.getSerializedSize();
        od.writeInt(header_sersize);
        head.header.writeTo(of);
        head.body.writeTo(of);

    }

    void closePBF() throws IOException {
        of.close();
    }

    private static class PBFBlob {
        private final Fileformat.BlobHeader header;
        private final Fileformat.Blob body;

        PBFBlob(String blobtype, byte[] payload) {
            int size = payload.length;
            Deflater deflater = new Deflater();
            deflater.setInput(payload);
            deflater.finish();
            byte[] out = new byte[size];
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
