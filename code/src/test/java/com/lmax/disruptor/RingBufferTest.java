/*
 * Copyright 2011 LMAX Ltd., modified by Jamie Allen to use Scala port.
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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.jamieallen.sdisruptor.Consumer;
import com.jamieallen.sdisruptor.ConsumerBarrier;
import com.jamieallen.sdisruptor.NoOpConsumer;
import com.jamieallen.sdisruptor.RingBuffer;
import com.jamieallen.sdisruptor.support.TestConsumer;
import com.lmax.disruptor.support.DaemonThreadFactory;
import com.lmax.disruptor.support.StubEntry;
import com.lmax.disruptor.support.TestWaiter;

public class RingBufferTest
{
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    private final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 20, null, null);
    private final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier(new Consumer[0]);
    {
  			final NoOpConsumer<StubEntry> noOpConsumer = new NoOpConsumer<StubEntry>(ringBuffer);
  			final NoOpConsumer[] noOpConsumers = new NoOpConsumer[] { noOpConsumer };
  			ringBuffer.consumersToTrack_(noOpConsumers);
    }

    @Test
    public void shouldClaimAndGet() throws Exception
    {
        assertEquals(-1L, ringBuffer.cursor());

        StubEntry expectedEntry = new StubEntry(2701);

        StubEntry oldEntry = ringBuffer.nextEntry();
        oldEntry.copy(expectedEntry);
        ringBuffer.commit(oldEntry);

        long sequence = consumerBarrier.waitFor(0);
        assertEquals(0, sequence);

        StubEntry entry = ringBuffer.entry(sequence);
        assertEquals(expectedEntry, entry);

        assertEquals(0L, ringBuffer.cursor());
    }

    @Test
    public void shouldClaimAndGetWithTimeout() throws Exception
    {
        assertEquals(-1L, ringBuffer.cursor());

        StubEntry expectedEntry = new StubEntry(2701);

        StubEntry oldEntry = ringBuffer.nextEntry();
        oldEntry.copy(expectedEntry);
        ringBuffer.commit(oldEntry);

        long sequence = consumerBarrier.waitFor(0, 5, TimeUnit.MILLISECONDS);
        assertEquals(0, sequence);

        StubEntry entry = ringBuffer.entry(sequence);
        assertEquals(expectedEntry, entry);

        assertEquals(0L, ringBuffer.cursor());
    }


    @Test
    public void shouldGetWithTimeout() throws Exception
    {
        long sequence = consumerBarrier.waitFor(0, 5, TimeUnit.MILLISECONDS);
        assertEquals(-1L, sequence);
    }

    @Test
    public void shouldClaimAndGetInSeparateThread() throws Exception
    {
        Future<List<StubEntry>> messages = getMessages(0, 0);

        StubEntry expectedEntry = new StubEntry(2701);

        StubEntry oldEntry = ringBuffer.nextEntry();
        oldEntry.copy(expectedEntry);
        ringBuffer.commit(oldEntry);

        assertEquals(expectedEntry, messages.get().get(0));
    }

    @Test
    public void shouldClaimAndGetMultipleMessages() throws Exception
    {
        int numMessages = ringBuffer.capacity();
        for (int i = 0; i < numMessages; i++)
        {
            StubEntry entry = ringBuffer.nextEntry();
            entry.setValue(i);
            ringBuffer.commit(entry);
        }

        int expectedSequence = numMessages - 1;
        long available = consumerBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = 0; i < numMessages; i++)
        {
            assertEquals(i, ringBuffer.entry(i).getValue());
        }
    }

    @Test
    public void shouldWrap() throws Exception
    {
        int numMessages = ringBuffer.capacity();
        int offset = 1000;
        for (int i = 0; i < numMessages + offset ; i++)
        {
            StubEntry entry = ringBuffer.nextEntry();
            entry.setValue(i);
            ringBuffer.commit(entry);
        }

        int expectedSequence = numMessages + offset - 1;
        long available = consumerBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = offset; i < numMessages + offset; i++)
        {
            assertEquals(i, ringBuffer.entry(i).getValue());
        }
    }

    @Test
    public void shouldSetAtSpecificSequence() throws Exception
    {
        long expectedSequence = 5;

        StubEntry expectedEntry = ringBuffer.claimEntryAtSequence(expectedSequence);
        expectedEntry.setValue((int) expectedSequence);
        ringBuffer.commitWithForce(expectedEntry);

        long sequence = consumerBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, sequence);

        StubEntry entry = ringBuffer.entry(sequence);
        assertEquals(expectedEntry, entry);

        assertEquals(expectedSequence, ringBuffer.cursor());
    }

    @Test
    public void shouldPreventProducersOvertakingConsumerWrapPoint() throws InterruptedException
    {
        final int ringBufferSize = 4;
        final CountDownLatch latch = new CountDownLatch(ringBufferSize);
        final AtomicBoolean producerComplete = new AtomicBoolean(false);
        final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, ringBufferSize, null, null);
        final TestConsumer consumer = new TestConsumer(ringBuffer.createConsumerBarrier(new Consumer[0]));
        final TestConsumer[] consumers = new TestConsumer[] { consumer };
        ringBuffer.consumersToTrack_(consumers);

        Thread thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                for (int i = 0; i <= ringBufferSize; i++)
                {
                    StubEntry entry = ringBuffer.nextEntry();
                    entry.setValue(i);
                    ringBuffer.commit(entry);
                    latch.countDown();
                }

                producerComplete.set(true);
            }
        });
        thread.start();

        latch.await();
        assertThat(Long.valueOf(ringBuffer.cursor()), is(Long.valueOf(ringBufferSize - 1)));
        assertFalse(producerComplete.get());

        consumer.run();
        thread.join();

        assertTrue(producerComplete.get());
    }

    private Future<List<StubEntry>> getMessages(final long initial, final long toWaitFor)
        throws InterruptedException, BrokenBarrierException
    {
        final CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
        final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier(new Consumer[0]);

        final Future<List<StubEntry>> f = EXECUTOR.submit(new TestWaiter(cyclicBarrier, consumerBarrier, initial, toWaitFor));

        cyclicBarrier.await();

        return f;
    }
}
