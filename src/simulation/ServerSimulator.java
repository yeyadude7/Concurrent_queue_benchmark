package simulation;

import core.IQueue;
import core.MetricsRecorder;
import lockbased.CLHLock;
import lockbased.LockBasedQueue;
import lockbased.SimpleLock;

public class ServerSimulator {
    public static void main(String[] args) {
        SimpleLock lock = new CLHLock();
        IQueue<Request> ingress = new LockBasedQueue<>(lock);
        MetricsRecorder metrics = new MetricsRecorder();

        int clients = 4;
        int workers = 4;
        int requestsPerClient = 50_000;

        // start workers
        WorkerPool pool = new WorkerPool(workers, ingress, metrics);
        pool.startAll();

        // start clients
        Thread[] generators = new Thread[clients];
        for (int i = 0; i < clients; i++) {
            generators[i] = new Thread(
                new RequestGenerator(ingress, requestsPerClient, 50, metrics),
                "client-" + i);
            generators[i].start();
        }

        long start = System.nanoTime();
        for (Thread t : generators) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }

        // send shutdown signals
        for (int i = 0; i < workers; i++) ingress.enqueue(null);
        pool.joinAll();
        long end = System.nanoTime();

        metrics.printSummary(end - start);
    }
}
