package org.hylly.mtk2garmin;

import java.util.concurrent.atomic.AtomicLong;

class FeatureIDProvider {
    private final AtomicLong nodeIDCounter = new AtomicLong(10000000000L);
    private final AtomicLong wayIDCounter = new AtomicLong(10000000000L);
    private final AtomicLong relationIDCounter = new AtomicLong(10000000000L);

    long getNodeID() {
        return nodeIDCounter.getAndIncrement();
    }

    long getWayID() {
        return wayIDCounter.getAndIncrement();
    }

    long getRelationID() {
        return relationIDCounter.getAndIncrement();
    }

}
