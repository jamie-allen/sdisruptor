/*
 * Copyright 2011 Jamie Allen, added to LMAX test support package to use for Scala port.
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
package com.jamieallen.sdisruptor.support;

import com.jamieallen.sdisruptor.Consumer;
import com.jamieallen.sdisruptor.ConsumerBarrier;
import com.jamieallen.sdisruptor.RingBuffer;
import com.lmax.disruptor.support.StubEntry;

public final class TestConsumer implements Consumer
{
    private final ConsumerBarrier<StubEntry> consumerBarrier;
    private volatile long sequence = RingBuffer.InitialCursorValue;

    public TestConsumer(final ConsumerBarrier<StubEntry> consumerBarrier)
    {
        this.consumerBarrier = consumerBarrier;
    }

    @Override
    public long sequence()
    {
        return sequence;
    }

    @Override
    public void sequence_(final long sequence)
    {
        this.sequence = sequence;
    }

    @Override
    public void halt()
    {
    }

    @Override
    public void run()
    {
        try
        {
            consumerBarrier.waitFor(0L);
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
        ++sequence;
    }
}
