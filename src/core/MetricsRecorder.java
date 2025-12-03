package core;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    public void recordEnqueue(long nanos) {
        totalEnqTime.addAndGet(nanos);
        enqOps.incrementAndGet();
    }

    public void recordControlEnqueue(long nanos) {
        totalEnqTime.addAndGet(nanos);
        controlEnqOps.incrementAndGet();
    }

    public void recordDequeue(long nanos) {
        totalDeqTime.addAndGet(nanos);
        deqOps.incrementAndGet();
    }

    public void recordControlDequeue(long nanos) {
        totalDeqTime.addAndGet(nanos);
        controlDeqOps.incrementAndGet();
    }

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

    /** Print to console only. */
    public void printSummary(long totalRuntimeNanos) {
        printSummaryInternal(totalRuntimeNanos, null);
    }

    /**
     * Print to console and save to a timestamped file under the given prefix.
     * Example: "src/results/threads_8/BatchQueue"
     * → writes to "src/results/threads_8/BatchQueue_results_2025-10-30_15-46-02.txt"
     */
    public void printSummaryToFile(String pathPrefix, long totalRuntimeNanos) {
        printSummaryInternal(totalRuntimeNanos, pathPrefix);
    }

    // ============ INTERNAL IMPLEMENTATION ============

    private void printSummaryInternal(long totalRuntimeNanos, String pathPrefix) {
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
        sb.append(String.format(
                "Ops count  → Enq: %d (+%d control)  Deq: %d (+%d control)  Processed: %d%n",
                enqOps.get(), controlEnqOps.get(),
                deqOps.get(), controlDeqOps.get(),
                processedRequests.get()));

        // Always print to console
        System.out.println(sb);

        if (pathPrefix != null) {
            try {
                // Timestamp for unique naming
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                String filename = pathPrefix + "_results_" + timestamp + ".txt";
                Path filePath = Paths.get(filename);

                Files.createDirectories(filePath.getParent());

                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(sb.toString());
                }

                System.out.println("Results written to: " + filename + "\n");
            } catch (IOException e) {
                System.err.println("Error writing results: " + e.getMessage());
            }
        }
    }
}
