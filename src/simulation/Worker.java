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

            // Nothing available yet → back off and retry
            if (req == null) {
                try {
                    Thread.sleep(0, 1000); // 1 µs pause to reduce contention
                } catch (InterruptedException ignored) {}
                continue;
            }

            // Poison pill → control dequeue, then exit thread
            if (req.getPayload() == null) {
                metrics.recordControlDequeue(end - start);
                break;
            }

            // Normal dequeue event
            metrics.recordDequeue(end - start);

            // Mark processing completion and record latency
            req.markDequeued();
            long latency = req.getLatencyNanos();
            if (latency <= 0) latency = 1; // avoid zero in stats
            metrics.recordRequestLatency(latency);

            // Simulate synthetic work (CPU-bound busy wait)
            busyWait(ThreadLocalRandom.current().nextInt(
                    meanWorkMicros / 2, meanWorkMicros * 3 / 2));
        }
    }

    /** Simulate CPU-bound work proportional to meanWorkMicros. */
    private void busyWait(long micros) {
        long target = micros * 1_000; // convert µs → ns
        long start = System.nanoTime();
        while (System.nanoTime() - start < target) {
            // spin
        }
    }
}
