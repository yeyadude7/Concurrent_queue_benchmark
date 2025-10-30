package core;

public interface IQueue<T> {
    void enqueue(T item);
    T dequeue();
}
