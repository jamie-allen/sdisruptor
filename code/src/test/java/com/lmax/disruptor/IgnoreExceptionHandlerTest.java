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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.jamieallen.sdisruptor.AbstractEntry;
import com.jamieallen.sdisruptor.ExceptionHandler;
import com.jamieallen.sdisruptor.IgnoreExceptionHandler;
import com.lmax.disruptor.support.TestEntry;

@RunWith(JMock.class)
public final class IgnoreExceptionHandlerTest
{
    private final Mockery context = new Mockery();

    public IgnoreExceptionHandlerTest()
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);
    }

    @Test
    public void shouldHandleAndIgnoreException()
    {
        final Exception ex = new Exception();
        final AbstractEntry entry = new TestEntry();

        final Logger logger = context.mock(Logger.class);

        context.checking(new Expectations()
        {
            {
                oneOf(logger).log(Level.INFO, "Exception processing: " + entry, ex);
            }
        });

        ExceptionHandler exceptionHandler = new IgnoreExceptionHandler(logger);
        exceptionHandler.handle(ex, entry);
    }
}
