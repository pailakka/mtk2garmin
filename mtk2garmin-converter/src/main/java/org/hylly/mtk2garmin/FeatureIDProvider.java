package org.hylly.mtk2garmin;

import java.util.concurrent.atomic.AtomicLong;

class FeatureIDProvider {
    private AtomicLong nodeIDCounter = new AtomicLong(50000000000L);
    private AtomicLong wayIDCounter = new AtomicLong(50000000000L);
    private AtomicLong relationIDCounter = new AtomicLong(50000000000L);

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
