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
package com.lmax.disruptor.support;

import com.lmax.disruptor.BatchHandler;
import com.lmax.disruptor.collections.Histogram;

public final class LatencyStepHandler implements BatchHandler<ValueEntry>
{
    private final FunctionStep functionStep;
    private final Histogram histogram;
    private final long nanoTimeCost;

    public LatencyStepHandler(final FunctionStep functionStep, final Histogram histogram, final long nanoTimeCost)
    {
        this.functionStep = functionStep;
        this.histogram = histogram;
        this.nanoTimeCost = nanoTimeCost;
    }

    @Override
    public void onAvailable(final ValueEntry entry) throws Exception
    {
        switch (functionStep)
        {
            case ONE:
            case TWO:
                break;

            case THREE:
                long duration = System.nanoTime() - entry.getValue();
                duration /= 3;
                duration -= nanoTimeCost;
                histogram.addObservation(duration);
                break;
        }
    }

    @Override
    public void onEndOfBatch() throws Exception
    {
    }
}
