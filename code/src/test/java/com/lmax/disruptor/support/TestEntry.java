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
package com.lmax.disruptor.support;

import com.jamieallen.sdisruptor.AbstractEntry;
import com.jamieallen.sdisruptor.EntryFactory;

public final class TestEntry
    extends AbstractEntry
{
		private long _sequence = -1L;

    public long _sequence()
    {
    		return _sequence;
    }
    
    public void _sequence_$eq(long newValue)
    {
    		_sequence = newValue;
    }

		@Override
    public String toString()
    {
        return "Test Entry";
    }

    public final static EntryFactory<TestEntry> ENTRY_FACTORY = new EntryFactory<TestEntry>()
    {
        public TestEntry create()
        {
            return new TestEntry();
        }
    };
}
