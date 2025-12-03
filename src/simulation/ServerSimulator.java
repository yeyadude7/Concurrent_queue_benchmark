package simulation;

import core.IQueue;
import core.MetricsRecorder;
import queue.LockBasedQueue;
import queue.BatchQueue;
import queue.MSQueue;
import queue.BackoffBatchQueue;

public class ServerSimulator {

    public static void main(String[] args) {
        try {
            // ======== Configuration ========
            int clients = 4;               // number of producer threads
            int workers = 4;               // number of consumer threads
            int requestsPerClient = 10_000; // total = 40,000 requests
            int maxClientDelayMicros = 50;  // simulate variable client arrival
            int meanWorkerMicros = 1000;    // average synthetic work per request

            // ======== Initialization ========
            IQueue<Request> ingress = new BackoffBatchQueue<>();
            MetricsRecorder metrics = new MetricsRecorder();

            // ======== Start workers FIRST ========
            WorkerPool pool = new WorkerPool(workers, ingress, metrics, meanWorkerMicros);
            pool.startAll();

            // ======== Start producers ========
            Thread[] generators = new Thread[clients];
            for (int i = 0; i < clients; i++) {
                generators[i] = new Thread(
                    new RequestGenerator(ingress, requestsPerClient, maxClientDelayMicros, metrics),
                    "client-" + i
                );
                generators[i].start();
            }

            // ======== Timing & Synchronization ========
            long start = System.nanoTime();

            // Wait for all producers to finish sending requests
            for (Thread t : generators) {
                try { t.join(); } catch (InterruptedException ignored) {}
            }

            // ======== Enqueue poison pills (control messages) ========
            for (int i = 0; i < workers; i++) {
                long enqStart = System.nanoTime();
                ingress.enqueue(new Request(-1, null)); // poison pill
                long enqEnd = System.nanoTime();
                metrics.recordControlEnqueue(enqEnd - enqStart);
            }

            // Wait for workers to finish
            pool.joinAll();

            long end = System.nanoTime();

            // ======== Print final metrics ========
            // Derive queue name dynamically (e.g., "MCSLockQueue" or "LockBasedQueue")
            String queueName = ingress.getClass().getSimpleName();
            metrics.printSummaryToFile(queueName, end - start);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
