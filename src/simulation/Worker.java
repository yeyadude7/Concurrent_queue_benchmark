package simulation;

import core.IQueue;
import core.MetricsRecorder;
import java.util.concurrent.ThreadLocalRandom;

public class Worker implements Runnable {
    private final IQueue<Request> queue;
    private final MetricsRecorder metrics;
    private final int meanWorkMicros;

    public Worker(IQueue<Request> queue, MetricsRecorder metrics, int meanWorkMicros) {
        this.queue = queue;
        this.metrics = metrics;
        this.meanWorkMicros = meanWorkMicros;
    }

    @Override
    public void run() {
        while (true) {
            long start = System.nanoTime();
            Request req = queue.dequeue();
            long end = System.nanoTime();
            metrics.recordDequeue(end - start);

            if (req == null) break;           // exit condition

            req.markDequeued();               // mark processing finish
            metrics.recordRequestLatency(req.getLatencyNanos());
            busyWait(ThreadLocalRandom.current().nextInt(meanWorkMicros / 2, meanWorkMicros * 3 / 2));
        }

    }

    private void busyWait(long micros) {
        long target = micros * 1_000;  // µs → ns conversion only once
        long start = System.nanoTime();
        while (System.nanoTime() - start < target) {}
    }

}
