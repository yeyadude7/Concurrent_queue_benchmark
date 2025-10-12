package lockbased;

import core.IQueue;
import java.util.LinkedList;
import java.util.Queue;

public class LockBasedQueue<T> implements IQueue<T> {
    private final Queue<T> queue;
    private final SimpleLock lock;

    public LockBasedQueue(SimpleLock lock) {
        this.queue = new LinkedList<>();
        this.lock = lock;
    }

    @Override
    public void enqueue(T item) {
        lock.lock();
        try {
            queue.add(item);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T dequeue() {
        lock.lock();
        try {
            return queue.poll();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        return queue.size();
    }
}
