package queue;

import core.IQueue;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Backoff Batch Queue (BBQ)
 * --------------------------
 * A lock-free batching queue that introduces exponential backoff
 * on failed CAS attempts during tail updates.
 *
 * Design motivation:
 *   - Standard BatchQueue (BQ) reduces contention by amortizing CAS ops.
 *   - However, simultaneous batch flushes can still collide at the shared tail.
 *   - BBQ adds temporal backoff to spread out retries, smoothing contention.
 *
 * Analogy:
 *   TTAS → BackoffLock :: BatchQueue → BackoffBatchQueue
 */
public class BackoffBatchQueue<T> implements IQueue<T> {

    /* ---------- Node structure (Michael–Scott style) ---------- */
    private static final class Node<E> {
        final E value;
        final AtomicReference<Node<E>> next = new AtomicReference<>(null);
        Node(E v) { this.value = v; }
    }

    /* ---------- Atomic head/tail pointers ---------- */
    private final AtomicReference<Node<T>> head;
    private final AtomicReference<Node<T>> tail;

    /* ---------- Thread-local batching buffer ---------- */
    private static final class LocalBuf<E> {
        Node<E> first, last;
        int size;

        void add(Node<E> n) {
            if (first == null) {
                first = last = n;
            } else {
                last.next.lazySet(n);
                last = n;
            }
            size++;
        }

        boolean isEmpty() { return size == 0; }
        void clear() { first = last = null; size = 0; }
    }

    private final ThreadLocal<LocalBuf<T>> local =
            ThreadLocal.withInitial(LocalBuf::new);

    /* ---------- Parameters ---------- */
    private final int batchThreshold;
    private static final int MIN_DELAY_NS = 50;     // tune as needed
    private static final int MAX_DELAY_NS = 50000;  // 50 µs max backoff

    public BackoffBatchQueue() {
        this(16); // default batch size
    }

    public BackoffBatchQueue(int batchThreshold) {
        if (batchThreshold < 2) batchThreshold = 2;
        this.batchThreshold = batchThreshold;

        Node<T> dummy = new Node<>(null);
        head = new AtomicReference<>(dummy);
        tail = new AtomicReference<>(dummy);
    }

    /* ---------- Public API ---------- */

    @Override
    public void enqueue(T item) {
        LocalBuf<T> buf = local.get();

        if (buf.isEmpty()) {
            msEnqueue(item);
            return;
        }

        buf.add(new Node<>(item));
        if (buf.size >= batchThreshold) {
            spliceLocalBatch(buf);
        }
    }

    @Override
    public T dequeue() {
        while (true) {
            Node<T> h = head.get();
            Node<T> t = tail.get();
            Node<T> next = h.next.get();
            if (h == head.get()) {
                if (next == null) {
                    LocalBuf<T> buf = local.get();
                    if (!buf.isEmpty()) {
                        spliceLocalBatch(buf);
                        continue;
                    }
                    return null;
                }
                T value = next.value;
                if (head.compareAndSet(h, next)) {
                    return value;
                }
            }
        }
    }

    public void flushLocal() {
        LocalBuf<T> buf = local.get();
        if (!buf.isEmpty()) spliceLocalBatch(buf);
    }

    /* ---------- Core logic ---------- */

    /** Single-item MSQ enqueue (fast path). */
    private void msEnqueue(T item) {
        Node<T> newNode = new Node<>(item);
        while (true) {
            Node<T> t = tail.get();
            Node<T> next = t.next.get();
            if (t == tail.get()) {
                if (next == null) {
                    if (t.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(t, newNode);
                        return;
                    }
                } else {
                    tail.compareAndSet(t, next);
                }
            }
        }
    }

    /** Batched enqueue with adaptive exponential backoff on CAS contention. */
    private void spliceLocalBatch(LocalBuf<T> buf) {
        Node<T> first = buf.first;
        Node<T> last = buf.last;
        if (first == null) return;

        int failCount = 0;
        while (true) {
            Node<T> t = tail.get();
            Node<T> next = t.next.get();

            if (t == tail.get()) {
                if (next == null) {
                    if (t.next.compareAndSet(null, first)) {
                        tail.compareAndSet(t, last);
                        buf.clear();
                        return;
                    } else {
                        // CAS failed — apply backoff delay before retry
                        pauseBackoff(failCount++);
                    }
                } else {
                    // help advance tail
                    tail.compareAndSet(t, next);
                }
            }
        }
    }

    /* ---------- Backoff utility ---------- */

    private void pauseBackoff(int failCount) {
        // Exponential backoff: 50ns * 2^failCount, capped at MAX_DELAY_NS
        long delay = Math.min(MAX_DELAY_NS, (long) MIN_DELAY_NS << Math.min(failCount, 10));
        long start = System.nanoTime();
        while (System.nanoTime() - start < delay) {
            Thread.onSpinWait();
        }
    }
}
