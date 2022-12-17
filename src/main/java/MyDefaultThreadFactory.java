// FIXME: Add documentation for this class and methods

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A factory for creating MyDefaultThread objects.
 */
public class MyDefaultThreadFactory implements ThreadFactory {
    
    /** The Constant poolNumber. */
    static final AtomicInteger poolNumber = new AtomicInteger(1);
    
    /** The group. */
    final ThreadGroup group;
    
    /** The thread number. */
    final AtomicInteger threadNumber = new AtomicInteger(1);
    
    /** The name prefix. */
    final String namePrefix;

    /**
     * Instantiates a new my default thread factory.
     *
     * @param threadPrefix the thread prefix
     */
    public MyDefaultThreadFactory(String threadPrefix) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null)? s.getThreadGroup() :
                             Thread.currentThread().getThreadGroup();
        namePrefix = threadPrefix + "-" +
                      poolNumber.getAndIncrement() +
                     "-thread-";
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
     */
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
                              namePrefix + threadNumber.getAndIncrement(),
                              0);
        if (t.isDaemon())
            t.setDaemon(false);
        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}