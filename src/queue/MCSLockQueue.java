package queue;

import core.IQueue;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

public class MCSLockQueue<T> implements IQueue<T> {

    private static class QNode {
        volatile boolean locked = false;
        volatile QNode next = null;
    }

    private static class MCSLock {
        private final AtomicReference<QNode> tail = new AtomicReference<>(null);
        private final ThreadLocal<QNode> myNode = ThreadLocal.withInitial(QNode::new);

        public void lock() {
            QNode node = myNode.get();
            QNode pred = tail.getAndSet(node);
            if (pred != null) {
                node.locked = true;
                pred.next = node;
                while (node.locked) Thread.onSpinWait();
            }
        }

        public void unlock() {
            QNode node = myNode.get();
            if (node.next == null) {
                if (tail.compareAndSet(node, null)) return;
                while (node.next == null) Thread.onSpinWait();
            }
            node.next.locked = false;
            node.next = null;
        }
    }

    private final LinkedList<T> queue = new LinkedList<>();
    private final MCSLock lock = new MCSLock();

    @Override
    public void enqueue(T item) {
        lock.lock();
        try {
            queue.addLast(item);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T dequeue() {
        lock.lock();
        try {
            return queue.isEmpty() ? null : queue.removeFirst();
        } finally {
            lock.unlock();
        }
    }
}
