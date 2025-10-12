package lockbased;

import java.util.concurrent.atomic.AtomicReference;

public class CLHLock implements SimpleLock {
    private static class QNode {
        volatile boolean locked = false;
    }

    private final AtomicReference<QNode> tail;
    private final ThreadLocal<QNode> myNode;
    private final ThreadLocal<QNode> myPred;

    public CLHLock() {
        tail = new AtomicReference<>(new QNode());
        myNode = ThreadLocal.withInitial(QNode::new);
        myPred = new ThreadLocal<>();
    }

    @Override
    public void lock() {
        QNode node = myNode.get();
        node.locked = true;
        QNode pred = tail.getAndSet(node);
        myPred.set(pred);

        // spin on predecessor's locked flag
        while (pred.locked) {
            // busy-wait locally cached variable
        }
    }

    @Override
    public void unlock() {
        QNode node = myNode.get();
        node.locked = false;
        myNode.set(myPred.get()); // reuse predecessor node for next round
    }
}
