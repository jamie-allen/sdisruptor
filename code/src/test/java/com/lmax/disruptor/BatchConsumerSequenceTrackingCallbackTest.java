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

import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import com.jamieallen.sdisruptor.BatchConsumer;
import com.jamieallen.sdisruptor.Consumer;
import com.jamieallen.sdisruptor.ConsumerBarrier;
import com.jamieallen.sdisruptor.RingBuffer;
import com.jamieallen.sdisruptor.SequenceTrackerCallback;
import com.jamieallen.sdisruptor.SequenceTrackingHandler;
import com.lmax.disruptor.support.StubEntry;

public class BatchConsumerSequenceTrackingCallbackTest
{
    private final CountDownLatch callbackLatch = new CountDownLatch(1);
    private final CountDownLatch onEndOfBatchLatch = new CountDownLatch(1);

    @Test
    public void shouldReportProgressByUpdatingSequenceViaCallback()
        throws Exception
    {
        final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 16, null, null);
        final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier(new Consumer[0]);
        final SequenceTrackingHandler<StubEntry> handler = new TestSequenceTrackingHandler();
        final BatchConsumer<StubEntry> batchConsumer = new BatchConsumer<StubEntry>(consumerBarrier, handler);
        final BatchConsumer[] batchConsumers = new BatchConsumer[] { batchConsumer };
        ringBuffer.consumersToTrack_(batchConsumers);

        Thread thread = new Thread(batchConsumer);
        thread.setDaemon(true);
        thread.start();

        assertEquals(-1L, batchConsumer.sequence());
        ringBuffer.commit(ringBuffer.nextEntry());

        callbackLatch.await();
        assertEquals(0L, batchConsumer.sequence());

        onEndOfBatchLatch.countDown();
        assertEquals(0L, batchConsumer.sequence());

        batchConsumer.halt();
        thread.join();
    }

    private class TestSequenceTrackingHandler implements SequenceTrackingHandler<StubEntry>
    {
        private SequenceTrackerCallback sequenceTrackerCallback;

        @Override
        public void setSequenceTrackerCallback(final SequenceTrackerCallback sequenceTrackerCallback)
        {
            this.sequenceTrackerCallback = sequenceTrackerCallback;
        }

        @Override
        public void onAvailable(final StubEntry entry)
        {
            sequenceTrackerCallback.onCompleted(entry.sequence());
            callbackLatch.countDown();
        }

        @Override
        public void onEndOfBatch()
        {
        		try
        		{
        				onEndOfBatchLatch.await();
        		}
        		catch (final InterruptedException ie)
        		{
        				fail(String.format("InterruptedException occurred: %s", ie.getMessage()));
        		}
        }
    }
}
