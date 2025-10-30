package queue;

import core.IQueue;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Server-Adapted Batch Queue (lock-free, MSQ-style with per-thread batching).
 *
 * Design:
 * - Head/Tail are MS-queue pointers (dummy head; singly linked list).
 * - Each thread holds a local buffer (linked list of nodes).
 * - Enqueue:
 *     * Fast path: direct MSQ enqueue if no pending locals (low latency).
 *     * Batched path: if there are pending locals (including the new item),
 *       splice the whole local list onto the shared tail in one shot.
 * - Dequeue: standard MSQ dequeue from shared list.
 *
 * Notes:
 * - This variant omits BQ’s announcements/futures (EMF-linearizability machinery)
 *   but keeps the key contention-reduction idea: apply multiple ops in a single
 *   tail splice to minimize shared-structure CAS traffic.
 */
public class BatchQueue<T> implements IQueue<T> {

    // ===== Node & pointers (MS-queue style) =====
    private static final class Node<E> {
        final E value;
        final AtomicReference<Node<E>> next = new AtomicReference<>(null);
        Node(E v) { this.value = v; }
    }

    // Head points to dummy; tail to last
    private final AtomicReference<Node<T>> head;
    private final AtomicReference<Node<T>> tail;

    // ===== Per-thread local buffer (singly linked list) =====
    private static final class LocalBuf<E> {
        Node<E> first, last;
        int size;

        void add(Node<E> n) {
            if (first == null) {
                first = last = n;
            } else {
                // chain locally; no atomics needed (thread-local)
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

    // Tune: when local buffer reaches this, we force a splice
    private final int batchThreshold;

    public BatchQueue() {
        this(16); // default: small batches; adjust if desired
    }

    public BatchQueue(int batchThreshold) {
        if (batchThreshold < 2) batchThreshold = 2;
        this.batchThreshold = batchThreshold;

        Node<T> dummy = new Node<>(null);
        head = new AtomicReference<>(dummy);
        tail = new AtomicReference<>(dummy);
    }

    // ===== Public API =====

    @Override
    public void enqueue(T item) {
        LocalBuf<T> buf = local.get();

        if (buf.isEmpty()) {
            // Fast path (paper’s: opsQueue empty → EnqueueToShared)
            msEnqueue(item);
            return;
        }

        // We already have pending locals: add and maybe splice the whole list
        buf.add(new Node<>(item));
        if (buf.size >= batchThreshold) {
            spliceLocalBatch(buf);
        }
    }

    @Override
    public T dequeue() {
        // Standard MS-queue dequeue
        while (true) {
            Node<T> h = head.get();
            Node<T> t = tail.get();
            Node<T> next = h.next.get();
            if (h == head.get()) {
                if (next == null) {
                    // Shared list empty → try to flush small local tail if we have any
                    LocalBuf<T> buf = local.get();
                    if (!buf.isEmpty()) {
                        spliceLocalBatch(buf);
                        // retry once after splice
                        continue;
                    }
                    return null;
                }
                // read value before CAS
                T value = next.value;
                if (head.compareAndSet(h, next)) {
                    return value; // dequeued
                }
                // else retry
            }
        }
    }

    /**
     * Optional: let producers force a flush of a partially-filled local batch.
     * (Call at the end of a producer loop if its batches are often smaller than the threshold.)
     */
    public void flushLocal() {
        LocalBuf<T> buf = local.get();
        if (!buf.isEmpty()) spliceLocalBatch(buf);
    }

    // ===== Internals =====

    /** Single-op MSQ enqueue (paper’s EnqueueToShared fast path). */
    private void msEnqueue(T item) {
        Node<T> newNode = new Node<>(item);
        while (true) {
            Node<T> t = tail.get();
            Node<T> next = t.next.get();
            if (t == tail.get()) {
                if (next == null) {
                    // try link new node at tail.next
                    if (t.next.compareAndSet(null, newNode)) {
                        // swing tail to new node
                        tail.compareAndSet(t, newNode);
                        return;
                    }
                } else {
                    // help advance tail
                    tail.compareAndSet(t, next);
                }
            }
        }
    }

    /** Splice the entire thread-local linked list [first..last] at the shared tail in one CAS sequence. */
    private void spliceLocalBatch(LocalBuf<T> buf) {
        Node<T> first = buf.first;
        Node<T> last  = buf.last;
        if (first == null) return;

        // Append [first..last] to tail, then advance tail to 'last'.
        while (true) {
            Node<T> t = tail.get();
            Node<T> next = t.next.get();
            if (t == tail.get()) {
                if (next == null) {
                    // Try to link our batch
                    if (t.next.compareAndSet(null, first)) {
                        // Move tail straight to 'last' (helpers will do this too if we lose this CAS)
                        tail.compareAndSet(t, last);
                        buf.clear();
                        return;
                    }
                    // else: someone else linked; fall through to help advance
                } else {
                    // someone else enqueued; help advance tail
                    tail.compareAndSet(t, next);
                }
            }
        }
    }
}
