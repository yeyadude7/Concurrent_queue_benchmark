package lockbased;

import java.util.concurrent.atomic.AtomicBoolean;

public class TASLock implements SimpleLock {
    private final AtomicBoolean state = new AtomicBoolean(false);

    @Override
    public void lock() {
        while (state.getAndSet(true)) {
            // busy-wait
        }
    }

    @Override
    public void unlock() {
        state.set(false);
    }
}
