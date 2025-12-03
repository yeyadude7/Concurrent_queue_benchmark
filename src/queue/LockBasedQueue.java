package queue;

import core.IQueue;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

public class LockBasedQueue<T> implements IQueue<T> {
    private final LinkedList<T> queue = new LinkedList<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void enqueue(T item) {
        lock.lock();
        try {
            queue.addLast(item);
            // System.out.println("[ENQ] Added: " + item);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T dequeue() {
        lock.lock();
        try {
            T item = queue.isEmpty() ? null : queue.removeFirst();
            // if (item != null)
            //     System.out.println("[DEQ] Got: " + item);
            return item;
        } finally {
            lock.unlock();
        }
    }
}