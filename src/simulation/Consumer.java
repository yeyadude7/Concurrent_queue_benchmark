// src/simulation/Consumer.java
package simulation;

import core.IQueue;
import core.Message;
import core.MetricsRecorder;

public class Consumer implements Runnable {
    private final IQueue<Message> queue;
    private final MetricsRecorder metrics;
    private static final boolean DEBUG = false;

    public Consumer(IQueue<Message> q, MetricsRecorder m) {
        this.queue = q; this.metrics = m;
    }

    @Override
    public void run() {
        if (DEBUG) System.out.println(Thread.currentThread().getName()
            + " using MetricsRecorder@" + System.identityHashCode(metrics));

        while (true) {
            long t0 = System.nanoTime();
            Message msg = queue.dequeue();           // blocking or non-blocking
            long t1 = System.nanoTime();
            if (msg == null) {
                // Non-blocking queue may return null briefly when empty.
                // Don't exit on null; just spin politely.
                metrics.recordDequeue(t1 - t0);
                Thread.onSpinWait();
                continue;
            }

            metrics.recordDequeue(t1 - t0);

            if (msg.isPoison()) {                    // ✅ uniform termination
                metrics.recordControlDequeue(t1 - t0);
                break;
            }

            msg.markDequeued();
            metrics.recordRequestLatency(msg.getLatencyNanos());
        }
    }
}
