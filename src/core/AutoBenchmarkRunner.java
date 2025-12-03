package core;

import queue.*;
import simulation.Request;
import simulation.RequestGenerator;
import simulation.ServerSimulator;
import simulation.WorkerPool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Automates benchmarking of all queue variants
 * across different producer counts.
 *
 * Usage:
 *   javac -d bin src/core/*.java src/queue/*.java src/simulation/*.java
 *   java -cp bin core.AutoBenchmarkRunner
 */
public class AutoBenchmarkRunner {

    public static void main(String[] args) {
        // Producer counts to test
        List<Integer> producerCounts = List.of(4, 8, 16, 32);
        List<Integer> requestSizes = List.of(10_000, 50_000, 200_000, 500_000);
        
        for (int requestsPerClient: requestSizes) {

        System.out.println("\n==============================================");
        System.out.println(">>> Starting workload: " + requestsPerClient + " requests per client");
        System.out.println("==============================================");
            for (int producers : producerCounts) {
                int workers = Math.max(4, producers / 2);
                int totalRequests = producers * requestsPerClient;

                System.out.printf("%n=== Running tests with %d producers, %d workers, %d total requests ===%n",
                        producers, workers, totalRequests);

                // Create result subfolder
                String subdir = "src/results/threads_" + producers + "_" + requestsPerClient + "_reqPerClient";
                new File(subdir).mkdirs();

                // Test each queue type
                logProgress("LockBasedQueue", producers, workers, requestsPerClient);
                testQueueVariant("LockBasedQueue", new LockBasedQueue<>(), subdir,
                        producers, workers, requestsPerClient);
                logProgress("BatchQueue", producers, workers, requestsPerClient);
                testQueueVariant("BatchQueue", new BatchQueue<>(), subdir,
                        producers, workers, requestsPerClient);
                logProgress("BackoffBatchQueue", producers, workers, requestsPerClient);
                testQueueVariant("BackoffBatchQueue", new BackoffBatchQueue<>(), subdir,
                        producers, workers, requestsPerClient);
                logProgress("MSQueue", producers, workers, requestsPerClient);
                testQueueVariant("MSQueue", new MSQueue<>(), subdir,
                        producers, workers, requestsPerClient);
                
            }
        }
        System.out.println("\nAll automated benchmarks completed.");
    }

    // core/AutoBenchmarkRunner.java → corrected test method
    private static void testQueueVariant(
            String name, IQueue<Message> queue, String subdir,
            int producers, int consumers, int messagesPerProducer) {

        System.out.println("\nRunning " + name + " ...");

        MetricsRecorder metrics = new MetricsRecorder();
        List<Thread> prod = new ArrayList<>();
        List<Thread> cons = new ArrayList<>();

        // Start watchdog for this variant
        Thread wd = startWatchdog(name, 10_000); // 10 seconds timeout

        // Start consumers first
        for (int i = 0; i < consumers; i++)
            cons.add(new Thread(new simulation.Consumer(queue, metrics), "consumer-" + i));
        cons.forEach(Thread::start);

        // Start producers
        for (int i = 0; i < producers; i++)
            prod.add(new Thread(new simulation.Producer(queue, i * messagesPerProducer, messagesPerProducer, metrics),
                    "producer-" + i));

        long start = System.nanoTime();
        prod.forEach(Thread::start);

        // Wait for producers
        for (Thread t : prod)
            try { t.join(); } catch (InterruptedException ignored) {}

        // Send one poison pill per consumer
        for (int i = 0; i < consumers; i++) {
            long t0 = System.nanoTime();
            queue.enqueue(Message.poisonPill());
            long t1 = System.nanoTime();
            metrics.recordControlEnqueue(t1 - t0);
        }

        // Wait for consumers
        for (Thread t : cons)
            try { t.join(); } catch (InterruptedException ignored) {}

        long end = System.nanoTime();

        // Stop watchdog — run completed successfully
        wd.interrupt();

        // Print summary
        String pathPrefix = subdir + "/" + name;
        metrics.printSummaryToFile(pathPrefix, end - start);
        System.out.println(" → " + name + " benchmark completed.\n");
    }

    private static void logProgress(String queueName, int producers, int workers, int requestsPerClient) {
        System.out.printf("Starting benchmark: Queue=%s, Producers=%d, Workers=%d, Requests/Client=%d%n",
                queueName, producers, workers, requestsPerClient);
    }

    private static Thread startWatchdog(String label, long timeoutMs) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeoutMs);
                System.err.println("\n[WATCHDOG] " + label +
                        " exceeded " + timeoutMs + " ms. Dumping thread states:\n");
                Thread.getAllStackTraces().forEach((t, st) -> {
                    System.err.println(" - " + t.getName() + " : " + t.getState());
                    for (StackTraceElement e : st)
                        System.err.println("   at " + e);
                    System.err.println();
                });
            } catch (InterruptedException ignored) {
                // Normal completion — watchdog canceled
            }
        }, "watchdog-" + label);

        watchdog.setDaemon(true); // Won’t block JVM shutdown
        watchdog.start();
        return watchdog;
    }

}
