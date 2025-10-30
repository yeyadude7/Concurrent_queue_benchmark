package queue;

import core.IQueue;

public class ServerAdaptedBQ<T> implements IQueue<T> {
    @Override
    public void enqueue(T item) {
        // TODO: Implement NUMA-local batching logic
    }

    @Override
    public T dequeue() {
        // TODO: Implement NUMA-aware dequeue logic
        return null;
    }
}
