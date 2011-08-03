/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import com.lmax.disruptor.support.Operation;
import com.lmax.disruptor.support.ValueEntry;
import com.lmax.disruptor.support.ValueMutationHandler;
import com.lmax.disruptor.support.ValueMutationQueueConsumer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 *
 * MultiCast a series of items between 1 producer and 3 consumers.
 *
 *           +----+
 *    +----->| C0 |
 *    |      +----+
 *    |
 * +----+    +----+
 * | P0 |--->| C1 |
 * +----+    +----+
 *    |
 *    |      +----+
 *    +----->| C2 |
 *           +----+
 *
 *
 * Queue Based:
 * ============
 *                 take
 *   put     +====+    +----+
 *    +----->| Q0 |<---| C0 |
 *    |      +====+    +----+
 *    |
 * +----+    +====+    +----+
 * | P0 |--->| Q1 |<---| C1 |
 * +----+    +====+    +----+
 *    |
 *    |      +====+    +----+
 *    +----->| Q2 |<---| C2 |
 *           +====+    +----+
 *
 * P0 - Producer 0
 * Q0 - Queue 0
 * Q1 - Queue 1
 * Q2 - Queue 2
 * C0 - Consumer 0
 * C1 - Consumer 1
 * C2 - Consumer 2
 *
 *
 * Disruptor:
 * ==========
 *                            track to prevent wrap
 *             +-------------------+---------+---------+
 *             |                   |         |         |
 *             |                   v         v         v
 * +----+    +====+    +====+    +----+    +----+    +----+
 * | P0 |--->| RB |<---| CB |    | C0 |    | C1 |    | C2 |
 * +----+    +====+    +====+    +----+    +----+    +----+
 *      claim      get    ^        |         |         |
 *                        |        |         |         |
 *                        +--------+---------+---------+
 *                                     waitFor
 *
 * P0 - Producer 0
 * RB - RingBuffer
 * CB - ConsumerBarrier
 * C0 - Consumer 0
 * C1 - Consumer 1
 * C2 - Consumer 2
 *
 * </pre>
 */
@SuppressWarnings("unchecked")
public final class MultiCast1P3CPerfTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_CONSUMERS = 3;
    private static final int SIZE = 1024 * 32;
    private static final long ITERATIONS = 1000 * 1000 * 500;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_CONSUMERS);

    private final long[] results = new long[NUM_CONSUMERS];
    {
        for (long i = 0; i < ITERATIONS; i++)
        {
            results[0] = Operation.ADDITION.op(results[0], i);
            results[1] = Operation.SUBTRACTION.op(results[1], i);
            results[2] = Operation.AND.op(results[2], i);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final ArrayBlockingQueue<Long>[] blockingQueues = new ArrayBlockingQueue[NUM_CONSUMERS];
    {
        blockingQueues[0] = new ArrayBlockingQueue<Long>(SIZE);
        blockingQueues[1] = new ArrayBlockingQueue<Long>(SIZE);
        blockingQueues[2] = new ArrayBlockingQueue<Long>(SIZE);
    }

    private final ValueMutationQueueConsumer[] queueConsumers = new ValueMutationQueueConsumer[NUM_CONSUMERS];
    {
        queueConsumers[0] = new ValueMutationQueueConsumer(blockingQueues[0], Operation.ADDITION);
        queueConsumers[1] = new ValueMutationQueueConsumer(blockingQueues[1], Operation.SUBTRACTION);
        queueConsumers[2] = new ValueMutationQueueConsumer(blockingQueues[2], Operation.AND);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEntry> ringBuffer =
        new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.YIELDING);

    private final ConsumerBarrier<ValueEntry> consumerBarrier = ringBuffer.createConsumerBarrier();

    private final ValueMutationHandler[] handlers = new ValueMutationHandler[NUM_CONSUMERS];
    {
        handlers[0] = new ValueMutationHandler(Operation.ADDITION);
        handlers[1] = new ValueMutationHandler(Operation.SUBTRACTION);
        handlers[2] = new ValueMutationHandler(Operation.AND);
    }

    private final BatchConsumer[] batchConsumers = new BatchConsumer[NUM_CONSUMERS];
    {
        batchConsumers[0] = new BatchConsumer<ValueEntry>(consumerBarrier, handlers[0]);
        batchConsumers[1] = new BatchConsumer<ValueEntry>(consumerBarrier, handlers[1]);
        batchConsumers[2] = new BatchConsumer<ValueEntry>(consumerBarrier, handlers[2]);
        ringBuffer.setTrackedConsumers(batchConsumers);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues()
        throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runQueuePass(final int passNumber) throws InterruptedException
    {
        Future[] futures = new Future[NUM_CONSUMERS];
        for (int i = 0; i < NUM_CONSUMERS; i++)
        {
            queueConsumers[i].reset();
            futures[i] = EXECUTOR.submit(queueConsumers[i]);
        }

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            final Long value = Long.valueOf(i);
            blockingQueues[0].put(value);
            blockingQueues[1].put(value);
            blockingQueues[2].put(value);
        }

        final long expectedSequence = ITERATIONS - 1;
        while (getMinimumSequence(queueConsumers) < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        for (int i = 0; i < NUM_CONSUMERS; i++)
        {
            queueConsumers[i].halt();
            futures[i].cancel(true);
            Assert.assertEquals(results[i], queueConsumers[i].getValue());
        }

        return opsPerSecond;
    }

    private long getMinimumSequence(final ValueMutationQueueConsumer[] queueConsumers)
    {
        long minimum = Long.MAX_VALUE;

        for (ValueMutationQueueConsumer consumer : queueConsumers)
        {
            long sequence = consumer.getSequence();
            minimum = minimum < sequence ? minimum : sequence;
        }

        return minimum;
    }

    @Override
    protected long runDisruptorPass(final int passNumber)
    {
        for (int i = 0; i < NUM_CONSUMERS; i++)
        {
            handlers[i].reset();
            EXECUTOR.submit(batchConsumers[i]);
        }

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            ValueEntry entry = ringBuffer.nextEntry();
            entry.setValue(i);
            ringBuffer.commit(entry);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (Util.getMinimumSequence(batchConsumers) < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        for (int i = 0; i < NUM_CONSUMERS; i++)
        {
            batchConsumers[i].halt();
            Assert.assertEquals(results[i], handlers[i].getValue());
        }

        return opsPerSecond;
    }
}
