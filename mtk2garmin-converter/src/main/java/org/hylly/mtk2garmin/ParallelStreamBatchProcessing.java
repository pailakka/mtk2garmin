package org.hylly.mtk2garmin;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ParallelStreamBatchProcessing {
    public static <ElementType> void processInBatch(Stream<ElementType> stream, int batchSize, Consumer<Collection<ElementType>> batchProcessor) {
        List<ElementType> newBatch = new ArrayList<>(batchSize);

        stream.forEach(element -> {
            List<ElementType> fullBatch;

            synchronized (newBatch) {
                if (newBatch.size() < batchSize) {
                    newBatch.add(element);
                    return;
                } else {
                    fullBatch = new ArrayList<>(newBatch);
                    newBatch.clear();
                    newBatch.add(element);
                }
            }

            batchProcessor.accept(fullBatch);
        });

        if (newBatch.size() > 0)
            batchProcessor.accept(new ArrayList<>(newBatch));
    }
}
