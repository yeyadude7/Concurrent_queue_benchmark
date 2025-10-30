package core;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsRecorder {
    private final AtomicLong totalEnqTime = new AtomicLong(0);
    private final AtomicLong totalDeqTime = new AtomicLong(0);
    private final AtomicLong totalReqLatency = new AtomicLong(0);

    private final AtomicLong enqOps = new AtomicLong(0);
    private final AtomicLong deqOps = new AtomicLong(0);
    private final AtomicLong processedRequests = new AtomicLong(0);

    private final AtomicLong controlEnqOps = new AtomicLong(0);
    private final AtomicLong controlDeqOps = new AtomicLong(0);

    // ============ RECORDERS ============

    /** Record enqueue duration for a normal data request. */
    public void recordEnqueue(long nanos) {
        totalEnqTime.addAndGet(nanos);
        enqOps.incrementAndGet();
    }

    /** Record enqueue duration for a control (poison) item. */
    public void recordControlEnqueue(long nanos) {
        totalEnqTime.addAndGet(nanos);
        controlEnqOps.incrementAndGet();
    }

    /** Record dequeue duration for a normal data request. */
    public void recordDequeue(long nanos) {
        totalDeqTime.addAndGet(nanos);
        deqOps.incrementAndGet();
    }

    /** Record dequeue duration for a control (poison) item. */
    public void recordControlDequeue(long nanos) {
        totalDeqTime.addAndGet(nanos);
        controlDeqOps.incrementAndGet();
    }

    /** Record end-to-end latency for a processed request. */
    public void recordRequestLatency(long nanos) {
        totalReqLatency.addAndGet(nanos);
        processedRequests.incrementAndGet();
    }

    // ============ ACCESSORS ============

    private static double safeDivide(long num, long denom) {
        return denom == 0 ? 0.0 : (double) num / denom;
    }

    public double getAvgEnqueueTime() { return safeDivide(totalEnqTime.get(), enqOps.get()); }
    public double getAvgDequeueTime() { return safeDivide(totalDeqTime.get(), deqOps.get()); }
    public double getAvgRequestLatency() { return safeDivide(totalReqLatency.get(), processedRequests.get()); }

    // ============ REPORTING ============

    private static String fmtTime(double ns) {
        if (ns >= 1_000_000.0) return String.format("%.2f ms", ns / 1_000_000.0);
        if (ns >= 1_000.0)     return String.format("%.2f µs", ns / 1_000.0);
        return String.format("%.2f ns", ns);
    }

    public void printSummary(long totalRuntimeNanos) {
        printSummaryInternal(totalRuntimeNanos, null);
    }

    public void printSummaryToFile(String queueName, long totalRuntimeNanos) {
        printSummaryInternal(totalRuntimeNanos, queueName);
    }

    private void printSummaryInternal(long totalRuntimeNanos, String queueName) {
        double runtimeMs = totalRuntimeNanos / 1e6;
        double runtimeSec = totalRuntimeNanos / 1e9;
        double throughput = processedRequests.get() / runtimeSec;

        StringBuilder sb = new StringBuilder();
        sb.append("==== Simulation Summary ====\n");
        sb.append(String.format("Total runtime: %.2f ms%n", runtimeMs));
        sb.append(String.format("Avg enqueue time: %s%n", fmtTime(getAvgEnqueueTime())));
        sb.append(String.format("Avg dequeue time: %s%n", fmtTime(getAvgDequeueTime())));
        sb.append(String.format("Avg end-to-end request latency: %s%n", fmtTime(getAvgRequestLatency())));
        sb.append(String.format("Throughput: %.2f reqs/sec%n", throughput));
        sb.append(String.format("Ops count  → Enq: %d (+%d control)  Deq: %d (+%d control)  Processed: %d%n",
                enqOps.get(), controlEnqOps.get(),
                deqOps.get(), controlDeqOps.get(),
                processedRequests.get()));

        // Print to console
        System.out.println(sb.toString());

        // If queueName is provided, also save to file
        if (queueName != null) {
            try {
                String folderPath = "src/results";
                Files.createDirectories(Paths.get(folderPath));
                String filename = folderPath + "/" + queueName + "_results.txt";

                try (FileWriter writer = new FileWriter(filename)) {
                    writer.write(sb.toString());
                }
                System.out.println("Results written to: " + filename);
            } catch (IOException e) {
                System.err.println("Error writing results: " + e.getMessage());
            }
        }
    }

}
