package core;

import queue.*;
import simulation.Consumer;
import simulation.Producer;

public class BenchmarkRunner {

    public static IQueue<Message> createQueue(QueueType type) {
        return switch (type) {
            case LOCK_BASED -> new LockBasedQueue();
            case MS_LOCK -> new MSQueue();
            case BATCH -> new BatchQueue();
            case BACKOFF_BQ -> new BackoffBatchQueue();
        };
    }

    public static void run(IQueue<Message> queue, int producers, int consumers, int messagesPerProducer) {
        MetricsRecorder metrics = new MetricsRecorder();
        Thread[] threads = new Thread[producers + consumers];

        for (int i = 0; i < producers; i++)
            threads[i] = new Thread(new Producer(queue, i * messagesPerProducer, messagesPerProducer, metrics));

        int msgsPerConsumer = producers * messagesPerProducer / consumers;
        for (int i = 0; i < consumers; i++)
            threads[producers + i] = new Thread(new Consumer(queue, metrics));

        long start = System.nanoTime();
        for (Thread t : threads) t.start();
        for (Thread t : threads) try { t.join(); } catch (InterruptedException ignored) {}
        long end = System.nanoTime();

        metrics.printSummary(end - start);
    }

    
    public static void main(String[] args) {
        QueueType type = QueueType.BACKOFF_BQ; // Change here
        IQueue<Message> queue = createQueue(type);

        System.out.println("Running benchmark with: " + type);
        run(queue, 4, 4, 100_000);
    }
}
