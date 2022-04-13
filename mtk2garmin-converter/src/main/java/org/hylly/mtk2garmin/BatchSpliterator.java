package org.hylly.mtk2garmin;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Pulls elements from one source and streams Lists of those elements in batches.
 *
 *
 * <code>
 * // This approach allows you to inspect some statistics as the stream progresses
 * System.out.println("Example 1");
 * BatchSpliterator<Integer> split = new BatchSpliterator<>(Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 3);
 * split.stream()
 * .peek(x -> System.out.println("Elements Consumed: " + split.getNumElementsConsumed()))
 * .peek(x -> System.out.println("Batches Produced : " + split.getNumBatchesProduced()))
 * .forEach(System.out::println);
 * <p>
 * <p>
 * // This example shows a simple example using the static helper function "batch"
 * System.out.println("Example 2");
 * BatchSpliterator.batch(Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 3).forEach(System.out::println);
 * <p>
 * // This example demonstrates that the underlying stream is consumed "live" not all at once:
 * System.out.println("Example 3");
 * Stream<String> stream = Stream.generate(new Supplier<String>() {
 * AtomicLong seq = new AtomicLong(1);
 *
 * @param <T>
 * @author Robert Harder
 * public String get() {
 * // Show exactly when get() is called on the underlying stream
 * String s = String.format("seq-%d", seq.getAndIncrement());
 * System.out.println("Generated: " + s);
 * LockSupport.parkNanos("simulated workload", TimeUnit.MILLISECONDS.toNanos((long) (100 * Math.random())));
 * return s;
 * }
 * });
 * BatchSpliterator.batch(stream.limit(10), 3).forEach(System.out::println);
 * </code>
 */
public class BatchSpliterator<T> implements Spliterator<List<T>> {
    private final static Logger logger = Logger.getLogger(BatchSpliterator.class.getName());

    // Some stats
    private final AtomicLong numElementsConsumed = new AtomicLong();
    private final AtomicLong numBatchesProduced = new AtomicLong();

    // Instance data
    private final int batchSize;
    private final Spliterator<T> sourceSpliterator;

    /**
     * Create batches from a {@link Spliterator} source.
     */
    public BatchSpliterator(Spliterator<T> sourceSpliterator, int batchSize) {
        this.batchSize = Math.max(1, batchSize);
        this.sourceSpliterator = sourceSpliterator;
    }

    /**
     * Create batches from a {@link Stream} source.
     */
    public BatchSpliterator(Stream<T> sourceStream, int batchSize) {
        this(sourceStream.spliterator(), batchSize);
    }

    /**
     * Create batches from an {@link Iterable} source.
     */
    public BatchSpliterator(Iterable<T> sourceIterable, int batchSize) {
        this(sourceIterable.spliterator(), batchSize);
    }

    /**
     * Returs a {@link Stream} that provides batches of type T.
     * The batches are in the form of a {@link List} with each
     * list having batchSize elements or fewer.  Only the last
     * batch will/might have fewer than batchSize elements.
     */
    public Stream<List<T>> stream() {
        return StreamSupport.stream(this, false);
    }

    /**
     * Static helper method for batching a source {@link Stream}.
     */
    public static <X> Stream<List<X>> batch(final Stream<X> source, final int size) {
        return StreamSupport.stream(new BatchSpliterator<>(source, size), false);
    }

    /**
     * Static helper method for batching a source {@link Spliterator}.
     */
    public static <X> Stream<List<X>> batch(final Spliterator<X> source, final int size) {
        return StreamSupport.stream(new BatchSpliterator<>(source, size), false);
    }


    /**
     * Static helper method for batching a source {@link Iterable}.
     */
    public static <X> Stream<List<X>> batch(final Iterable<X> source, final int size) {
        return StreamSupport.stream(new BatchSpliterator<>(source, size), false);
    }

    /**
     * Returns the number of source elements consumed so far.
     */
    public long getNumElementsConsumed() {
        return numElementsConsumed.get();
    }

    /**
     * Returns the number of batches produced so far.
     */
    public long getNumBatchesProduced() {
        return numBatchesProduced.get();
    }

    /**
     * Part of the {@link Spliterator} class, this method is called
     * with a {@link Consumer} that expects to receive a batch when
     * a batch is ready.  The method instead returns false if no batch
     * is ready (as in, the underlying stream was finished).
     */
    @Override
    public boolean tryAdvance(Consumer<? super List<T>> action) {
        List<T> batch = new ArrayList<>(batchSize);

        while (sourceSpliterator.tryAdvance(batch::add)) {
            numElementsConsumed.incrementAndGet();
            if (batch.size() >= batchSize) {
                break;
            }
        }

        if (batch.isEmpty()) {
            return false;
        } else {
            numBatchesProduced.incrementAndGet();
            action.accept(batch);
            return true;
        }
    }

    /**
     * Part of the {@link Spliterator} class, this method is called
     * when an attempt to parallelize the stream is made.
     * The underlying source {@link Spliterator#trySplit()} method is
     * called, and if that is successful, then a split will be made here too.
     */
    @Override
    public Spliterator<List<T>> trySplit() {
        Spliterator<T> s2 = sourceSpliterator.trySplit();
        if (s2 == null) {
            return null;
        } else {
            return new BatchSpliterator<>(s2, batchSize);
        }
    }

    /**
     * Part of the {@link Spliterator} class, this method is called
     * to make an estimate of the number of batches that will be produced.
     * The estimate is based on the source
     * {@link Spliterator#estimateSize()} method.
     */
    @Override
    public long estimateSize() {
        return (sourceSpliterator.estimateSize() / batchSize) + 1;
    }

    /**
     * Part of the {@link Spliterator} class, this method
     * returns no characteristic hints (returns a zero).
     */
    @Override
    public int characteristics() {
        return 0;
    }
}
