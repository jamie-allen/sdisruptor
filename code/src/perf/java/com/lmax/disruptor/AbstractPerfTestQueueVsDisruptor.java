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

import org.junit.Assert;

public abstract class AbstractPerfTestQueueVsDisruptor
{
    protected void testImplementations()
        throws Exception
    {
        final int RUNS = 3;
        long disruptorOps = 0L;
        long queueOps = 0L;

        for (int i = 0; i < RUNS; i++)
        {
            System.gc();

            disruptorOps = runDisruptorPass(i);
            queueOps = runQueuePass(i);

            printResults(getClass().getSimpleName(), disruptorOps, queueOps, i);
        }

        Assert.assertTrue("Performance degraded", disruptorOps > queueOps);
    }


    public static void printResults(final String className, final long disruptorOps, final long queueOps, final int i)
    {
        System.out.format("%s OpsPerSecond run %d: BlockingQueues=%d, Disruptor=%d\n",
                          className, Integer.valueOf(i), Long.valueOf(queueOps), Long.valueOf(disruptorOps));
    }

    protected abstract long runQueuePass(int passNumber) throws Exception;

    protected abstract long runDisruptorPass(int passNumber) throws Exception;

    protected abstract void shouldCompareDisruptorVsQueues() throws Exception;
}
