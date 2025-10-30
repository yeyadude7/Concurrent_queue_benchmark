package simulation;

import core.IQueue;
import core.MetricsRecorder;
import java.util.ArrayList;
import java.util.List;

public class WorkerPool {
    private final List<Thread> workers = new ArrayList<>();

    public WorkerPool(int size, IQueue<Request> queue, MetricsRecorder metrics, int meanWorkMicros) {
        for (int i = 0; i < size; i++) {
            workers.add(new Thread(new Worker(queue, metrics, meanWorkMicros), "worker-" + i));
        }
    }

    public void startAll() { workers.forEach(Thread::start); }

    public void joinAll() {
        for (Thread t : workers) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }
    }
}
