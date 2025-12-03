// src/simulation/Producer.java
package simulation;

import core.IQueue;
import core.Message;
import core.MetricsRecorder;

public class Producer implements Runnable {
    private final IQueue<Message> queue;
    private final int startId, messageCount;
    private final MetricsRecorder metrics;

    private static final boolean DEBUG = false;

    public Producer(IQueue<Message> q, int startId, int count, MetricsRecorder m) {
        this.queue = q; this.startId = startId; this.messageCount = count; this.metrics = m;
    }

    @Override
    public void run() {
        if (DEBUG) System.out.println(Thread.currentThread().getName()
            + " using MetricsRecorder@" + System.identityHashCode(metrics));

        for (int i = 0; i < messageCount; i++) {
            Message msg = new Message(startId + i, "msg-" + (startId + i));
            msg.markEnqueued();                       // ✅ needed for latency
            long t0 = System.nanoTime();
            queue.enqueue(msg);
            long t1 = System.nanoTime();
            metrics.recordEnqueue(t1 - t0);
        }
    }
}
