package queue;

import core.IQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Michael & Scott (1996) Lock-Free Queue
 * --------------------------------------
 * A classic non-blocking concurrent queue using CAS-based head/tail pointers.
 * 
 * This design is multi-producer, multi-consumer (MPMC) safe and forms the
 * foundation for Java's ConcurrentLinkedQueue. It scales well in NUMA/UMA
 * environments typical of modern servers.
 */
public final class MSQueue<T> implements IQueue<T> {

    /** Node structure for the linked queue. */
    private static final class Node<E> {
        final E value;
        final AtomicReference<Node<E>> next;

        Node(E value) {
            this.value = value;
            this.next = new AtomicReference<>(null);
        }
    }

    private final AtomicReference<Node<T>> head;
    private final AtomicReference<Node<T>> tail;

    public MSQueue() {
        Node<T> dummy = new Node<>(null);
        this.head = new AtomicReference<>(dummy);
        this.tail = new AtomicReference<>(dummy);
    }

    /** Lock-free enqueue using atomic tail updates. */
    @Override
    public void enqueue(T item) {
        Node<T> node = new Node<>(item);
        while (true) {
            Node<T> last = tail.get();
            Node<T> next = last.next.get();
            if (last == tail.get()) {
                if (next == null) {
                    // Try to link new node at the end
                    if (last.next.compareAndSet(null, node)) {
                        // Swing tail to the new node
                        tail.compareAndSet(last, node);
                        return;
                    }
                } else {
                    // Tail is lagging; advance it
                    tail.compareAndSet(last, next);
                }
            }
        }
    }

    /** Lock-free dequeue using atomic head updates. */
    @Override
    public T dequeue() {
        while (true) {
            Node<T> first = head.get();
            Node<T> last = tail.get();
            Node<T> next = first.next.get();

            if (first == head.get()) {
                if (first == last) {
                    if (next == null) {
                        // Queue empty
                        return null;
                    }
                    // Tail is behind; advance it
                    tail.compareAndSet(last, next);
                } else {
                    T value = next.value;
                    if (head.compareAndSet(first, next)) {
                        return value;
                    }
                }
            }
        }
    }
}
