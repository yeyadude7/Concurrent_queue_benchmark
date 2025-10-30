package core;

import queue.*;

public class BenchmarkRunner {

    public static IQueue<Message> createQueue(QueueType type) {
        return switch (type) {
            case LOCK_BASED -> new LockBasedQueue();
            case MCS_LOCK -> new MCSLockQueue();
            case BATCH -> new BatchQueue();
            case SERVER_BQ -> new ServerAdaptedBQ();
        };
    }

    public static void run(IQueue<Message> queue, int producers, int consumers, int messagesPerProducer) {
        MetricsRecorder metrics = new MetricsRecorder();
        Thread[] threads = new Thread[producers + consumers];

        for (int i = 0; i < producers; i++)
            threads[i] = new Thread(new Producer(queue, i * messagesPerProducer, messagesPerProducer, metrics));

        int msgsPerConsumer = producers * messagesPerProducer / consumers;
        for (int i = 0; i < consumers; i++)
            threads[producers + i] = new Thread(new Consumer(queue, msgsPerConsumer, metrics));

        long start = System.nanoTime();
        for (Thread t : threads) t.start();
        for (Thread t : threads) try { t.join(); } catch (InterruptedException ignored) {}
        long end = System.nanoTime();

        metrics.printSummary(end - start);
    }

    
    public static void main(String[] args) {
        QueueType type = QueueType.MCS_LOCK; // Change here
        IQueue<Message> queue = createQueue(type);

        System.out.println("Running benchmark with: " + type);
        run(queue, 4, 4, 100_000);
    }
}
