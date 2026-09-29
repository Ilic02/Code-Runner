package io.github.ilic02.coderunner.queue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RunQueueTest {

    @Test
    @Timeout(10)
    void submitsAndReturnsResult() throws Exception {
        RunQueue queue = new RunQueue();
        var future = queue.submit(() -> 40 + 2);
        assertEquals(42, future.get());
    }

    @Test
    @Timeout(10)
    void rejectsWhenWorkersAndQueueAreFull() throws Exception {
        RunQueue queue = new RunQueue();

        // 4 radnika + 20 mesta u redu = 24 posla koji mogu da čekaju istovremeno
        int occupying = 24;
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(occupying);

        for (int i = 0; i < occupying; i++) {
            queue.submit(() -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return null;
            });
        }

        // sačekaj da bar 4 posla stvarno počnu da rade (radnici zauzeti)
        // ostatak sedi u redu čekajući mesto, što je tačno stanje koje testiramo
        Thread.sleep(200);

        // 25. posao ne sme da stane nigde: ni radnici ni red nemaju mesta
        assertThrows(RunQueue.QueueFullException.class, () -> queue.submit(() -> null));

        release.countDown(); // pusti sve da završe, da test ne ostavi zaglavljene niti
    }
}