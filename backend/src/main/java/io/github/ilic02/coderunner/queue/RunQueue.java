package io.github.ilic02.coderunner.queue;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

@Component
public class RunQueue {

    private static final int WORKER_COUNT = 4;
    private static final int QUEUE_CAPACITY = 20;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            WORKER_COUNT, WORKER_COUNT,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY));

    /**
     * Zakazuje posao za izvršavanje. Baca RejectedExecutionException ako su
     * svi radnici zauzeti i red je pun (server je preopterećen).
     */
    public <T> Future<T> submit(Callable<T> task) {
        try {
            return executor.submit(task);
        } catch (RejectedExecutionException e) {
            throw new QueueFullException();
        }
    }

    public int queueSize() {
        return executor.getQueue().size();
    }

    public int activeCount() {
        return executor.getActiveCount();
    }

    public static class QueueFullException extends RuntimeException {
        public QueueFullException() {
            super("Server is at capacity, try again shortly");
        }
    }
}