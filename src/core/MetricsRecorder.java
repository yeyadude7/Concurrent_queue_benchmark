package core;

import java.util.concurrent.atomic.AtomicLong;

public class MetricsRecorder {
    private final AtomicLong totalEnqTime = new AtomicLong(0);
    private final AtomicLong totalDeqTime = new AtomicLong(0);
    private final AtomicLong totalReqLatency = new AtomicLong(0);
    private final AtomicLong enqOps = new AtomicLong(0);
    private final AtomicLong deqOps = new AtomicLong(0);
    private final AtomicLong processedRequests = new AtomicLong(0);

    public void recordEnqueue(long nanos) {
        totalEnqTime.addAndGet(nanos);
        enqOps.incrementAndGet();
    }

    public void recordDequeue(long nanos) {
        totalDeqTime.addAndGet(nanos);
        deqOps.incrementAndGet();
    }

    public void recordRequestLatency(long nanos) {
        totalReqLatency.addAndGet(nanos);
        processedRequests.incrementAndGet();
    }

    public double getAvgEnqueueTime() {
        return enqOps.get() == 0 ? 0 : totalEnqTime.get() / (double) enqOps.get();
    }

    public double getAvgDequeueTime() {
        return deqOps.get() == 0 ? 0 : totalDeqTime.get() / (double) deqOps.get();
    }

    public double getAvgRequestLatency() {
        return processedRequests.get() == 0 ? 0 : totalReqLatency.get() / (double) processedRequests.get();
    }

    public void printSummary(long totalRuntimeNanos) {
        System.out.println("\n==== Simulation Summary ====");
        System.out.printf("Total runtime: %.2f ms%n", totalRuntimeNanos / 1e6);
        System.out.printf("Avg enqueue time: %.2f ns%n", getAvgEnqueueTime());
        System.out.printf("Avg dequeue time: %.2f ns%n", getAvgDequeueTime());
        System.out.printf("Avg end-to-end request latency: %.2f µs%n", getAvgRequestLatency() / 1e3);
        System.out.printf("Throughput: %.2f reqs/sec%n",
            (processedRequests.get()) / (totalRuntimeNanos / 1e9));
    }
}
