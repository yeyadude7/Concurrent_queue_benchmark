package core;

import java.util.ArrayList;
import java.util.List;

import lockbased.CLHLock;
import lockbased.LockBasedQueue;
import lockbased.SimpleLock;
import lockbased.TASLock;

public class BenchmarkRunner {

    public static void run(IQueue<Message> queue, int producers, int consumers, int messagesPerProducer) {
        MetricsRecorder metrics = new MetricsRecorder();
        List<Thread> threads = new ArrayList<>();

        // Create producer threads
        for (int i = 0; i < producers; i++) {
            threads.add(new Thread(new Producer(queue, i * messagesPerProducer, messagesPerProducer, metrics)));
        }

        // Create consumer threads
        int totalMessages = producers * messagesPerProducer / consumers;
        for (int i = 0; i < consumers; i++) {
            threads.add(new Thread(new Consumer(queue, totalMessages, metrics)));
        }

        long start = System.nanoTime();
        threads.forEach(Thread::start);

        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }
        long end = System.nanoTime();

        metrics.printSummary(end - start);
    }

    // For quick testing:
    public static void main(String[] args) {
        // Use the LockBasedQueue with a CLH lock
        SimpleLock lock = new CLHLock();
        IQueue<Message> queue = new LockBasedQueue(lock);

        System.out.println("Running LockBasedQueue with CLHLock...");
        run(queue, 4, 4, 100_000);
    }
}
