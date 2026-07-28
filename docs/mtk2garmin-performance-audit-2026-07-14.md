# mtk2garmin end-to-end performance audit

Date: 2026-07-14

Status: audit and prioritized recommendations; no pipeline Implementation was changed

## Executive verdict

The current v2 pipeline has three different kinds of performance loss:

1. **Work that should not happen in a data run.** Every scheduled run rebuilds and pushes container images, then pulls them again. This cost about 19 minutes in the 2026-07-07 run and prevented the 2026-07-11 and 2026-07-14 runs from reaching payload work at all.
2. **Independent work forced into one serial chain.** Once <code>all_osm.osm.pbf</code> exists, the Garmin and Mapsforge branches are independent, but they run one after the other. The ideal overlap ceiling on the July payload is about 78 minutes; a realistic first benchmark target is 40–70 minutes after contention.
3. **Real hot loops inside the Rust tools.** <code>rs-ogr2osm</code> spends 49 minutes of the 93.5-minute maasto conversion in GDAL/GEOS simplification. <code>rs-mapsforge-writer</code> spends 59.7 of 78 minutes in its way filtering/staging pass while using an unexpectedly shallow node-index cache and an unbuffered 2.31 GB staged-way summary index.

The most valuable architectural direction is a **manifest-driven, resumable execution graph**:

- immutable tool-image releases are built separately;
- one input-snapshot manifest identifies all source payloads;
- every expensive stage records its input/config/tool fingerprints and output checksums;
- the Garmin and Mapsforge Modules execute as parallel branches with explicit resource budgets;
- publication preflights permissions, writes to a staging prefix, and exposes the final index only after all artifacts validate.

This direction improves both elapsed time and useful-work yield. The latest full-payload run took about **6h20m49s**, uploaded almost 16 GiB, and then failed on the final 69-byte <code>robots.txt</code> write. The two newer scheduled runs failed after about one minute during mandatory image construction. A fast pipeline that repeatedly discards valid stage outputs is still a slow production system.

For the first no-semantic-change tranche, a reasonable benchmark goal is **60–100 minutes off a fresh-snapshot critical path**, primarily from image-release separation and controlled branch overlap. Snapshot reuse can avoid another **45–49 minutes** when upstream data is unchanged. These figures are targets and upper bounds, not additive promises.

## Evidence policy

This report distinguishes:

- **Measured:** directly timed or counted in the inspected payload logs.
- **Upper bound:** the maximum time available in a measured bucket; the recommendation cannot save more than this.
- **Hypothesis:** a code-supported opportunity that still needs an A/B run.

All concurrency savings are hypotheses until tested on the production host. The host has eight physical cores, sixteen logical CPUs, 125 GiB RAM, and mirrored NVMe storage, so memory capacity is generous but CPU and shared-filesystem contention remain real.

## Scope and sources

The audit covered the live working trees and their current uncommitted state:

- <code>/home/teemu/mtk2garmin</code>: orchestration, input acquisition, Docker image construction, Garmin generation, packaging, publication, cleanup, and compatibility gates.
- <code>/home/teemu/rs-ogr2osm</code>: dataset conversion, topology synthesis, coordinate indexing, spooling, PBF writing, sorting, filtering, and merging.
- <code>/home/teemu/rs-mapsforge-writer</code>: node lookup, way staging, the way planner Module, way chunks and way chunk views, tile payload generation, and map writing.
- Real cron and test payload logs under <code>/home/teemu</code>, especially 2026-06-30, 2026-07-04, 2026-07-07, 2026-07-11, and 2026-07-14.

The Mapsforge terminology follows [CONTEXT.md](../../rs-mapsforge-writer/CONTEXT.md). No <code>CONTEXT.md</code> or ADR directory was present in the other two repositories. Existing Mapsforge optimization plans were checked so already-landed work such as global-encoded staging, multi-interval planning, disabled diagnostic intersections, and parallel tile payload workers is not proposed again.

The audit intentionally did not modify or benchmark the pipeline. The input payload is tens of gigabytes and a production run is several hours, so candidate changes should be validated with frozen snapshots and explicit parity gates.

## Current execution shape

    scheduled run
      |
      +-- image build/push/pull
      |
      +-- legacy incremental clients
      |
      +-- three serial OGC jobs/downloads
      |
      +-- five serial rs-ogr2osm conversions
      |     +-- sort each PBF
      |     +-- merge
      |     +-- sort/filter/sort/merge/sort/check
      |
      +-- Garmin branch
      |     +-- splitter
      |     +-- four serial mkgmap variants
      |
      +-- rs-mapsforge-writer
      |
      +-- OSX package
      +-- two serial NSIS packages
      |
      +-- assemble/hash/copy/upload
      +-- write live index and small site objects
      |
      +-- success-only cleanup

The central orchestration sequence is explicit in [convert_docker.sh](../mapcreator/convert_docker.sh#L146): merge, mkgmap, Mapsforge, OSX, NSIS, then publish. There is no stage manifest or resumable Interface.

## Production baseline

### Latest full-payload attempt: 2026-07-07

The log spans 03:00:01–09:20:50, or approximately **6h20m49s**. All expensive payload work completed and artifacts were uploaded, but the top-level run failed at [log line 40797](../../mtk2garmin_20260707030001-cron.log#L40797) because the live-site identity lacked <code>s3:PutObject</code> permission for <code>robots.txt</code>.

| Stage | Wall time | Classification | Evidence |
|---|---:|---|---|
| Image build, push, and pull before input work | about 19m18s | timestamp-derived | log start to first input stage; [image build Module](../mapcreator/convert_docker.sh#L112) |
| Two legacy input clients | 0m47s | measured | [log 32046](../../mtk2garmin_20260707030001-cron.log#L32046), [log 33903](../../mtk2garmin_20260707030001-cron.log#L33903) |
| Full OGC refresh | 45m56.6s | measured | [log 33960](../../mtk2garmin_20260707030001-cron.log#L33960) |
| Rust conversion, sort, filter, merge, check | 148m01.3s | measured | [log 34639](../../mtk2garmin_20260707030001-cron.log#L34639) |
| Splitter | 20m12.4s | measured | [log 36754](../../mtk2garmin_20260707030001-cron.log#L36754) |
| Four mkgmap variants | 38m18.2s | measured sum | [log 36793–36872](../../mtk2garmin_20260707030001-cron.log#L36793) |
| Garmin branch wrapper | 58m46.7s | measured | [log 36891](../../mtk2garmin_20260707030001-cron.log#L36891) |
| rs-mapsforge-writer | 78m01.1s | measured | [log 37771](../../mtk2garmin_20260707030001-cron.log#L37771) |
| OSX packaging | 0m42.3s | measured | [log 37868](../../mtk2garmin_20260707030001-cron.log#L37868) |
| Two NSIS installers | 20m51.6s | measured sum | [log 39247](../../mtk2garmin_20260707030001-cron.log#L39247), [log 40623](../../mtk2garmin_20260707030001-cron.log#L40623) |
| Local assembly plus S3 publication | 8m24.6s | measured | [log 40800](../../mtk2garmin_20260707030001-cron.log#L40800) |

The timed payload stages excluding image construction total about 6h01m31s. The four largest measured buckets are conversion/merge, Mapsforge, Garmin, and input refresh; together they account for about 5h31m.

### Three-run stability

| Stage | 2026-06-30 | 2026-07-04 | 2026-07-07 | Median |
|---|---:|---:|---:|---:|
| OGC refresh | 45.5m | 49.5m | 45.9m | 45.9m |
| Rust conversion and merge | 147.4m | 154.4m | 148.0m | 148.0m |
| Garmin branch | 62.4m | 63.8m | 58.8m | 62.4m |
| Mapsforge | 82.2m | 82.4m | 78.0m | 82.2m |
| NSIS total | 20.2m | 20.6m | 20.9m | 20.6m |
| Publish | 8.1m | 8.1m | 8.4m | 8.1m |

The 2026-07-04 and 2026-07-07 runs both failed at the final live <code>robots.txt</code> write. The 2026-06-30 run reached success cleanup. Therefore the table is a payload-performance baseline, not evidence that the last three top-level runs succeeded.

### Newer failures

The 2026-07-11 and 2026-07-14 jobs stopped after roughly one minute. The data path was never reached. Both failed at <code>apt update</code> in [ubuntugis-base/Dockerfile](../mapcreator/ubuntugis-base/Dockerfile#L1) because a mutable Apache Arrow repository key was unavailable:

- [2026-07-11 log line 162](../../mtk2garmin_20260711030001-cron.log#L162)
- [2026-07-14 log line 171](../../mtk2garmin_20260714030001-cron.log#L171)

This is direct evidence that image-release work is incorrectly coupled to the data-production Module.

### Payload and host scale

- Current national inputs are approximately 23.5 GB topography, 11.6 GB elevation, and 13.3 GB cadastral data.
- The retained 2026-07-07 publish tree is about 16 GiB.
- The Mapsforge output is 3,663,881,300 bytes.
- Four Garmin IMG outputs total about 5.5 GB.
- The host has eight physical cores, sixteen logical CPUs, 125 GiB RAM, and one ext4 filesystem backed by two Samsung NVMe devices in RAID1. About 388 GiB was free during the audit.

## Ranked recommendations

Savings in this table overlap. For example, pairwise mkgmap and parallel NSIS work may be hidden inside the wider Garmin/Mapsforge branch overlap and must not be counted twice.

| Rank | Candidate | Strength | Measured opportunity or ceiling | Primary risk |
|---:|---|---|---|---|
| 1 | Separate immutable image releases from scheduled data runs | Strong | about 19m/run, plus restores jobs currently blocked before payload work | stale or mismatched images |
| 2 | Add publish preflight and content-addressed stage resume | Strong | avoid repeating about 6h of valid work after a late publish failure | mixing generations |
| 3 | Execute Garmin and Mapsforge as resource-budgeted parallel branches | Strong direction; benchmark required | ideal overlap ceiling about 78m; first target 40–70m | CPU and I/O contention |
| 4 | Buffer the Mapsforge staged-way summary store and honor node-index controls | Strong code evidence | contained in 59.7m pass 4 and 4.5m global-store load; exact saving unknown | RSS and parity |
| 5 | Make input acquisition snapshot-aware and skip v1-only updates in v2 | Strong when unchanged | 45–49m refresh plus 47s legacy work | stale or mixed snapshots |
| 6 | Run independent rs-ogr2osm datasets with bounded concurrency | Moderate | 32.5m contention-free ceiling | slowing maasto through shared CPU/I/O |
| 7 | Parallelize rs-ogr2osm discovery/simplification across layers | Moderate/high-value | 49m GDAL/GEOS bucket; two-worker ceiling about 24m | GDAL/GEOS safety and deterministic order |
| 8 | Prove and remove redundant PBF sorts | Moderate | subset of the 22m post-conversion remainder | invalid type/ID ordering |
| 9 | Deepen rs-ogr2osm coordinate-index and spool Implementations | Moderate | 11.6m index and 8.3m maasto spool timers are measured pools | higher complexity and memory |
| 10 | Pair mkgmap variants and NSIS jobs | Moderate | about 18m mkgmap plus 10m NSIS ideal ceilings | CPU/memory overcommit |
| 11 | Reduce copy/hash/publication amplification | Moderate/low wall-time | 8.4m stage and more than 10 GB avoidable local writes | hard-link lifecycle mistakes |
| 12 | Reuse validated splitter areas | Exploratory | splitter is 20.2m; estimated saving only 3–8m | violating older-device granularity |

## Detailed findings

### 1. Build images as releases, not as payload stages

**Files:** [convert_docker.sh lines 112–133](../mapcreator/convert_docker.sh#L112), [lines 431–435](../mapcreator/convert_docker.sh#L431), [ubuntugis-base/Dockerfile](../mapcreator/ubuntugis-base/Dockerfile), [mkgmap Dockerfile](../mkgmap-converter/Dockerfile), [site Dockerfile](../site/Dockerfile)

**Problem:** <code>RUN_IMAGE_BUILD</code> defaults to one. A scheduled data run pulls GDAL, rebuilds and pushes the base image, builds and pushes the compose images, builds <code>rs-ogr2osm</code>, creates uncached daily additional data, then calls <code>docker compose pull</code>. Mutable bases, live package repositories, an unpinned latest splitter archive, and installer downloads make the data path depend on external release-time state.

**Solution direction:** create a deep image-release Module that emits immutable image digests and a release manifest. The scheduled pipeline consumes that manifest through a narrow Interface. Rebuild only when source, lockfiles, base digests, or explicit data-image inputs change.

**Benefits:**

- removes about 19 minutes from the measured July 7 critical path;
- eliminates redundant push/pull traffic;
- prevents package-repository failures from blocking unchanged payload processing;
- makes stage fingerprints and resumption trustworthy.

**Validation:** run the same frozen input once with the current image build and once with pinned image digests. Compare stage wall time, output validation, source commit, image digests, and configuration hashes.

### 2. Preflight publication and resume verified stages

**Files:** [generate_site.sh lines 26–62](../site/generate_site.sh#L26), [convert_docker.sh publish step](../mapcreator/convert_docker.sh#L295)

**Problem:** the publication Implementation assembles and hashes artifacts, copies them into a publish tree, uploads the large archive, then performs small live-site writes. A missing permission on the final 69-byte object caused both July 4 and July 7 to discard top-level success after more than six hours.

**Solution direction:** add two seams:

1. A publish preflight Adapter tests every required destination class using a canary prefix before expensive conversion.
2. A stage-manifest Adapter records source/config/tool fingerprints and output checksums for source acquisition, each converted dataset, merged PBF, Garmin artifacts, Mapsforge map, packages, and publish tree.

Resume is allowed only when the complete fingerprint and checksum set matches. Publication uses a staging prefix and makes the final index visible last.

**Benefits:**

- a publish-only retry repeats about eight minutes, not six hours;
- a failed Mapsforge or installer stage does not invalidate the independent Garmin branch;
- measurement artifacts can survive success-only build-root cleanup;
- immutable stage identity is the foundation for snapshot reuse and parallel execution.

**Validation:** produce artifacts with publishing disabled, alter one configuration hash, and prove only downstream manifests invalidate. Corrupt one cached artifact and prove checksum rejection. Simulate a denied live-site object and prove the failure occurs during preflight.

### 3. Replace the serial tail with a resource-budgeted execution graph

**Files:** [convert_docker.sh lines 146–165](../mapcreator/convert_docker.sh#L146), [Garmin script](../mkgmap-converter/run_mkgmap.sh), [NSIS calls](../mapcreator/convert_docker.sh#L278)

**Problem:** the merged PBF feeds two independent Modules, but the orchestrator does not expose that seam.

**Proposed shape:**

    validated all_osm.osm.pbf
      |
      +-- Garmin Module: splitter -> mkgmap variants -> OSX + NSIS
      |
      +-- Mapsforge Module: node lookup -> way plan -> tile payload -> map
      |
      +-- join on validated artifact manifests
      |
      +-- stage and publish

Within the Garmin branch, start with two mkgmap variants at a time using lower per-process job counts, and run the two NSIS packages concurrently. Do not launch all four 40 GiB-heap processes without measured resource limits.

**Measured basis:**

- Garmin branch: 58.8 minutes.
- Mapsforge: 78.0 minutes.
- OSX plus NSIS after Garmin: 21.6 minutes.
- Ideal sequential work hidden by the other branch: roughly 78 minutes.
- Mapsforge peak RSS: 13.06 GB, leaving memory headroom.
- Individual mkgmap runs average roughly six CPU cores from user/wall timing, so CPU contention is the limiting factor.

**Validation:** run sequential and concurrent variants against the identical frozen PBF. Capture total wall time, per-process CPU, peak and system RSS, major faults, and block I/O. Require the current Garmin subfile gate, Mapsforge reader/render parity, counts, and artifact semantics.

### 4. Deepen the Mapsforge staged-way and node-lookup Modules

**Files:** [args.rs lines 438–442](../../rs-mapsforge-writer/src/args.rs#L438), [main.rs lines 449–553](../../rs-mapsforge-writer/src/main.rs#L449), [writer/mod.rs node-index cache](../../rs-mapsforge-writer/src/writer/mod.rs#L700), [staged summary writer](../../rs-mapsforge-writer/src/writer/mod.rs#L2398)

**Measured hotspot:**

- total Mapsforge time: 4,679,796 ms;
- way filtering/staging pass: 3,583,739 ms, or 76.6%;
- map writing: 655,132 ms;
- peak RSS: 13,064,908,800 bytes.

The production profile configures a 65,536-block cache, but the active DiskNodeIndex uses a fixed 4,096-block limit. [The production log reports](../../mtk2garmin_20260707030001-cron.log#L37228) 423,179,722 hits and **97,834,766 misses**. Every miss seeks, reads, and decodes a compressed block of up to 1,024 records.

At the same time, [the measured 2,307,735,896-byte staged-way summary index](../../mtk2garmin_20260707030001-cron.log#L37732) contains 60,729,892 fixed-size records. Its writer is a raw <code>File</code> and emits each summary through ten small writes; the reader likewise performs repeated small reads. This is a shallow Implementation at the hottest persistence seam.

**Solution direction, in order:**

1. Buffer summary reads/writes and encode one fixed record buffer per summary. Reuse coordinate encoding buffers and the existing in-place varint helpers.
2. Honor the configured node-index cache size; benchmark 4,096, 16,384, and 65,536 blocks.
3. Benchmark the existing memory node-lookup shape for the high-memory profile. Raw ID and coordinate arrays for 445 million nodes are roughly 7.1 GB before allocator details, within the configured budget.
4. If a flat memory index is not sufficiently local, use a hybrid lookup Adapter: dense arrays for the converter’s synthetic ID ranges and sparse blocks for original OSM IDs.
5. Only after those low-risk wins, explore a global way-chunk view that references shared encoded/decoded arenas instead of copying selected coordinates into each way chunk.

**Benefits:** the first two changes are local and directly target hundreds of millions of small operations. The deeper node-lookup Adapter raises locality for the entire 59.7-minute pass.

**Validation:** report pass 4, global-store load, cache hit/miss counts, major faults, RSS, and allocation profiles. Require identical map reader counts, debug signatures, tag inventories, and render parity.

### 5. Make input snapshots explicit

**Files:** [load_and_convert.sh](../mml-ogc-processes/load_and_convert.sh), [main.py lines 54–144](../mml-ogc-processes/main.py#L54), [v2 input configs](../../rs-ogr2osm/mtk_config/mtk2garmin_maasto.toml)

**Problem:** three full-national OGC jobs are submitted, polled, downloaded, and repackaged serially every run. No release/version fingerprint is consulted before starting. In a v2-only run, the two preceding legacy clients update GML/shapefile stores that the Rust configs do not read.

**Solution direction:** resolve authoritative release metadata first, create one atomic input-snapshot manifest, retain immutable payloads by checksum, and download only changed products. Submit independent remote jobs concurrently, then control download and SOZip concurrency separately. Skip legacy clients unless the v1 pipeline is selected.

**Measured pool:** 45.9 minutes on July 7, 49.5 minutes on July 4, plus 47 seconds of v1-only updates on July 7. A previously observed refresh reached 84 minutes, so caching also reduces variance.

**Validation:** compare consecutive upstream release identifiers and forced-fresh file checksums. A cached run and forced refresh with identical source identity must produce the same downstream semantic checks. Never mix release identifiers across the three products without an explicit policy.

### 6. Add bounded dataset concurrency in rs-ogr2osm

**Files:** [run-rs-full.sh lines 79–112](../../rs-ogr2osm/scripts/mtk/run-rs-full.sh#L79)

The five conversions run serially:

| Dataset | July wall time | Share of conversion sum |
|---|---:|---:|
| Maasto | 93.5m | 74.2% |
| Elevation | 18.9m | 15.0% |
| Property boundaries | 12.5m | 9.9% |
| Depth contours | 0.8m | 0.7% |
| Depth points | 0.3m | 0.2% |

The four non-maasto conversions total 32.5 minutes. That is the contention-free ceiling if they fit entirely under maasto; it is not an expected saving.

**Solution direction:** a bounded conversion scheduler with a global CPU, memory, and temporary-storage budget. Start with two simultaneous conversions, separate logs, and fail-fast cancellation. The scheduler is a shallow Adapter over existing converter invocations and need not change converter semantics.

**Validation:** alternate sequential/concurrency-two/sequential runs on identical cached inputs. Record CPU frequency/governor, aggregate CPU, iostat, system RSS, temp-disk high water, and output parity. A result is beneficial only if total wall time falls without making maasto disproportionately slower.

### 7. Parallelize the rs-ogr2osm Phase I geometry path

**Files:** [ogr_layer_reader.rs lines 20–76](../../rs-ogr2osm/src/ogr_layer_reader.rs#L20), [node_discovery.rs lines 184–270](../../rs-ogr2osm/src/node_discovery.rs#L184), [node_discovery_layer_reader.rs lines 86–121](../../rs-ogr2osm/src/node_discovery_layer_reader.rs#L86)

The OGR feature loop reads, simplifies/extracts, and emits one feature at a time through a shared coordinate-index builder and feature-spool writer. The configured worker threads primarily help later Lua batching; they do not make this Phase I loop deep.

For maasto:

- total conversion: 5,612.2s;
- discovery: 4,083.8s;
- GDAL/GEOS simplification: 2,934.0s, or 52.3% of the entire conversion;
- <code>suo</code> alone: 1,416.7s;
- <code>tieviiva</code>: 377.5s;
- <code>kallioalue</code>: 298.8s;
- <code>soistuma</code>: 293.3s;
- <code>virtavesikapea</code>: 254.8s.

**Solution direction:** use one Dataset/OGR/GEOS context per worker, produce deterministic per-layer spool segments and sorted coordinate chunks, then merge them through a coordinating Adapter. Start with two workers and the <code>suo</code> corpus. Do not replace topology-preserving polygon simplification with ordinary RDP.

**Upper bound:** doubling only the 2,934-second GDAL/GEOS bucket gives 24.5 minutes. Real savings will be lower because input decompression, coordinate indexing, and merging remain.

**Risk:** GDAL/GEOS context safety, concurrent ZIP/SQLite reads, temporary storage, deterministic feature order, and topology parity.

### 8. Make sorted PBF output a contract, then remove proven duplicate sorts

**Files:** [merge-rs-full.sh lines 37–80](../../rs-ogr2osm/scripts/mtk/merge-rs-full.sh#L37), [run-rs-full.sh lines 47–76](../../rs-ogr2osm/scripts/mtk/run-rs-full.sh#L47)

Each converter output is sorted, the five streams are merged, <code>all_direct</code> is sorted again, the supplemental Finland stream is filtered and sorted, the two sorted streams are merged, and the merged result is sorted again.

The five converter calls total about 126.0 minutes inside a 148.0-minute wrapper, so **22.0 minutes is the upper bound for all sorting, filtering, merging, checks, and orchestration combined**. The exact sort share is currently unmeasured.

Current positive-ID writer paths appear to emit deterministic node, way, and relation ordering, and merging verified sorted inputs should preserve order. That is a hypothesis until every path is validated.

**Solution direction:** make ordering a declared output contract at each Interface. Time every osmium invocation and validate extended file ordering. Remove one sort at a time only after the upstream Interface proves its contract.

**Validation:** monotonic type/ID verification, <code>osmium fileinfo -e</code>, <code>osmium check-refs</code>, entity counts, tag inventories, and downstream Garmin/Mapsforge parity. The pipeline has previously detected a node after a way, so a successful <code>check-refs</code> alone is insufficient.

### 9. Improve rs-ogr2osm index and spool locality

**Files:** [index.rs](../../rs-ogr2osm/src/index.rs), [feature_spool.rs](../../rs-ogr2osm/src/feature_spool.rs), [write_plan_disk.rs](../../rs-ogr2osm/src/write_plan_disk.rs)

Measured conversion-wide coordinate-index construction totals 693 seconds, or 11.6 minutes. The Implementation uses tiny unbuffered reads in external merge, durability syncs for disposable chunks, a machine-word mapping where a checked 32-bit representation is sufficient for current counts, and a fixed 50-million spill threshold disconnected from the 64 GB budget.

The maasto feature spool reached about 20.5 GB across thousands of files. It duplicates coordinate representations, serializes all non-null field names/values although the MTK Lua Adapter uses a known set, and replays many separately encoded files. Maasto’s feature/write-plan spool timers form an approximately 8.3-minute measured pool.

**Solution direction:** deepen these storage Modules before changing domain behavior:

- buffered or mapped merge cursors and capacity-aware output;
- disposable-file semantics without unnecessary durability sync;
- memory-budget-derived spill thresholds;
- compact checked index types;
- schema/field IDs and an explicit MTK field projection;
- length-framed buffered streams and separate way/relation replay.

**Validation:** add submetrics for chunk sort, spill, merge, index construction, spool encode/decode, and PBF compression. Compare 50M, 200M, and no-spill profiles. Gate counts, ID mapping, references, tag inventories, and output order.

### 10. Use pairwise Garmin and packaging concurrency

**Files:** [run_mkgmap.sh lines 22–30](../mkgmap-converter/run_mkgmap.sh#L22), [convert_docker.sh lines 278–289](../mapcreator/convert_docker.sh#L278)

Splitter runs once, then four 9–10 minute mkgmap variants execute serially. The standard and AMOLED styles differ materially, so these are independent computations rather than one map with a swapped TYP file. Two 10-minute NSIS packages are also independent and serial.

**Solution direction:** test two mkgmap processes at a time with six to eight jobs each, then two NSIS jobs concurrently. Treat this as the internal Implementation of the Garmin branch rather than a separate top-level pipeline.

**Upper bounds:** roughly 18–20 minutes for pairwise mkgmap and 10 minutes for NSIS. These savings overlap the branch-DAG candidate.

**Validation:** measure actual process RSS first. Require image entry counts, tile count, maximum RGN size, family/TYP identity, installer contents, and the current compatibility checker. Hash equality is not required if packaging timestamps are nondeterministic, but semantic contents must match.

### 11. Reduce publication copy and hashing amplification

**Files:** [run_mkgmap.sh lines 32–42](../mkgmap-converter/run_mkgmap.sh#L32), [generate_site.sh lines 26–47](../site/generate_site.sh#L26), [generate_site.py hashing](../site/generate_site.py#L274)

Large IMG files are copied under alternate names, copied into <code>output/dist</code>, hashed serially, then rsynced into another tree on the same filesystem before S3 upload. The final tree is approximately 16 GiB.

**Solution direction:** give immutable artifacts their final names, hard-link or move them through same-filesystem staging, and compute checksums once at the producing stage for the manifest. Preserve atomic visibility: upload into a staging prefix and publish indices last.

**Expected effect:** likely only one to five minutes of wall time, but more than 10 GB of local write amplification and failure-state disk growth can be removed.

**Validation:** verify inode and cleanup lifecycles in disposable fixture trees. No producer may mutate an artifact after manifest sealing.

### 12. Reuse splitter areas only behind the compatibility gate

Splitter spent 20m12s and produced 577 tiles. Reusing a validated areas template may avoid part of its planning work, but changing national data can push a tile over node or RGN limits.

This is exploratory and lower priority. Any experiment must retain:

- <code>GARMIN_SPLITTER_MAX_NODES=800000</code>;
- maximum subfile size of 4 MiB;
- maximum IMG size of 1900 MiB;
- maximum 1000 tiles;
- the current embedded TYP/FID compatibility requirements.

The July images passed with 577 tiles and maximum RGN sizes between 2.70 and 3.33 MiB. Raising max-nodes is not a performance recommendation: prior 1.6-million-node output produced only 288 tiles and failed the older-device subfile-size gate.

## Tool-specific secondary opportunities

### rs-ogr2osm

- Add a zero-tolerance fast path before projection/allocation in topology synthesis. Scan-collect time across July conversions is about 6.9 minutes, the maximum affected pool.
- Test lower compression for disposable per-dataset PBFs or parallel block compression, but measure the full conversion-plus-osmium-plus-consumer path because larger PBFs can move cost downstream.
- A/B extracted GeoPackages against <code>/vsizip/</code> inputs on NVMe, including extraction cost. OGR-next time is an upper bound, not proof that ZIP is slow.
- Keep current proven work: native-linear simplification planning, Rust line RDP for selected line layers, feature sidecar replay, external coordinate sorting, batched transforms, sorted DenseNodes, and parallel Lua batches.

### rs-mapsforge-writer

- In an explicit high-memory node mode, collapse count/reference/node-index passes where ordering permits. Count plus reference discovery plus node indexing cost about 306 seconds.
- Reuse persistent payload workers and worker-local scratch. Payload computation is about 243 seconds, only 5.2% of total runtime, so this follows pass-4 work.
- Remove avoidable tag-value allocation when <code>tag_values=false</code> and move rather than clone tag vectors. This primarily creates memory headroom.
- Do not reintroduce stale recommendations: exact diagnostic intersections are already disabled, multi-interval planning is active, global-encoded staging is active, and 16 tile workers are already configured.

### Garmin and packaging tools

- Benchmark a stable splitter area template only after higher-ranked work.
- Do not deduplicate standard versus AMOLED runs by changing only TYP; their styles differ.
- Pin the splitter release rather than downloading <code>splitter-latest</code> during image construction.
- Treat NSIS timestamp nondeterminism separately from semantic installer parity.

## Measurement and validation plan

### Instrumentation required before optimization

The top-level script exposes wall times but not a structured stage timeline. Add a retained run manifest with:

- stage name, start/end, exit status, and dependency identities;
- command/tool version and container digest;
- source, config, Lua, style, and executable hashes;
- wall, user, system, peak RSS, major faults, bytes read/written, and output sizes;
- host CPU model/governor/frequency, load, memory, disk free space, and cache-state policy;
- semantic validation results.

Time every osmium invocation separately. Preserve converter metrics and Mapsforge progress summaries outside transient build roots even when success cleanup keeps only publishable artifacts.

### Experiment discipline

1. Freeze input checksums and image digests.
2. Use alternating A/B/A or at least three medium repeats; report medians and variance.
3. Change one candidate at a time unless the experiment explicitly measures a new execution graph.
4. Compare critical-path wall time, not just summed process times.
5. Record system-wide CPU, memory, and block I/O for every concurrency test.
6. Reject a speedup that changes semantic output or violates resource headroom.

### Required parity gates

- rs-ogr2osm: entity counts, tag inventory, monotonic type/ID order, missing references, <code>osmium check-refs</code>, and representative geometry parity.
- Garmin: tile count, node occupancy where available, IMG entry inventory, maximum RGN/subfile size, final IMG size, FID/TYP identity, and older-device compatibility limits.
- Mapsforge: reader counts, tag inventory, debug signatures, representative renders, relation diagnostics, and map metadata.
- Packaging: archive member inventory, executable/install contents, checksums where deterministic, and public URL/index consistency.
- Publication: permissions preflight, staging-prefix completeness, final-index-last behavior, and safe retry.
- Cleanup: retain only publishable <code>output/dist</code> on success, preserve diagnostics on failure, and keep <code>RUN_CLEANUP=0</code> as the escape hatch.

## Recommended rollout sequence

### Phase 0 — stop wasting complete runs

1. Fix or remove the unauthorized live <code>robots.txt</code> write.
2. Preflight all publish permissions before conversion.
3. Set scheduled production to consume pinned image digests; move image construction to a separate release workflow.
4. Persist a run/stage manifest outside the cleanup root.

This phase is mostly operational locality and shallow Adapter work. It should precede CPU tuning because it makes every later benchmark reproducible and resumable.

### Phase 1 — no-semantic-change critical-path work

1. Buffer Mapsforge staged summary I/O and honor its node-index cache setting.
2. Benchmark 4K/16K/65K cache blocks and the high-memory node lookup.
3. Implement the Garmin/Mapsforge dependency graph with conservative CPU budgets.
4. Run two NSIS jobs concurrently and benchmark two mkgmap jobs at reduced internal parallelism.
5. Skip v1-only input clients on v2-only runs.

Target: demonstrate 60–100 minutes lower fresh-snapshot wall time without output changes. The target includes overlap and is not the sum of every ceiling.

### Phase 2 — avoid unchanged work

1. Introduce authoritative input-snapshot identity.
2. Add content-addressed stage manifests and verified resume.
3. Reuse unchanged per-dataset conversions and final branches.
4. Replace copy amplification with immutable artifact staging.

### Phase 3 — converter hotspot work

1. Benchmark bounded dataset concurrency.
2. Prototype two-worker per-layer discovery on <code>suo</code>.
3. Make sorted-output contracts executable and remove one redundant sort at a time.
4. Deepen coordinate-index and spool storage.

### Phase 4 — deeper Mapsforge work

1. Decide flat-memory versus hybrid node lookup from measured locality/RSS results.
2. Collapse input passes for explicit high-memory mode.
3. Explore a zero-copy global way-chunk view.
4. Revisit payload workers only after pass 4 is no longer dominant.

## Explicit non-goals and safeguards

- Do not raise Garmin splitter max-nodes or relax the 4 MiB subfile gate for speed.
- Do not replace topology-preserving polygon simplification with ordinary line RDP.
- Do not expose a partially uploaded release; stage first and publish the final index last.
- Do not launch four 40 GiB-heap mkgmap processes blindly.
- Do not remove an osmium sort based on assumption alone.
- Do not delete failed-run diagnostics; preserve the success-only cleanup contract.
- Do not mix cached artifacts from different input, config, style, Lua, tool, or image identities.
- Do not tune Mapsforge output buffering first: tile write I/O is tiny compared with payload computation and pass 4.

## Bottom line

The pipeline is not primarily limited by disk bandwidth or available RAM. Its largest immediate losses come from orchestration locality and shallow persistence Interfaces:

- release work embedded in the payload run;
- no verified resume after late failure;
- independent branches serialized;
- unbuffered or misconfigured hot stores;
- independent dataset and packaging jobs serialized.

Address those before changing geographic semantics. The first implementation candidate should be the manifest-driven execution graph, with image-release separation and publish preflight as its first slice. The first isolated code benchmark should be Mapsforge staged-summary buffering plus node-index cache wiring. The first converter research benchmark should be two-worker <code>suo</code> discovery on a frozen maasto snapshot.
