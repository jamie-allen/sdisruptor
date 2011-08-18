/*
 * Copyright 2011 LMAX Ltd., ported to Scala by Jamie Allen
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
package com.jamieallen.sdisruptor

import java.util.concurrent.atomic.AtomicLong;

object ClaimStrategy {
  val MultiThreaded = 'multiThreaded
  val SingleThreaded = 'singleThreaded
  
	def newInstance(option: Symbol): ClaimStrategy = {
	  option match {
	    case SingleThreaded => new SingleThreadedStrategy
	    case MultiThreaded => new MultiThreadedStrategy
	  }
	}

  /** Optimised strategy can be used when there is a single producer thread claiming {@link AbstractEntry}s.
   */
  class SingleThreadedStrategy extends ClaimStrategy {
    private var _sequence = -1L

    override def incrementAndGet() = {
      _sequence += 1
      _sequence
    }

    override def incrementAndGet(delta: Int) = {
      _sequence += delta
      _sequence
    }

    override def sequence_(sequence: Long) { _sequence = sequence }
  }
	
  /** Strategy to be used when there are multiple producer threads claiming {@link AbstractEntry}s.
	 */
  class MultiThreadedStrategy extends ClaimStrategy {
	  private val _sequence = new AtomicLong(-1L)
	
	  override def incrementAndGet() = _sequence.incrementAndGet
	  override def incrementAndGet(delta: Int) = _sequence.addAndGet(delta)
	  override def sequence_(sequence: Long) { _sequence.set(sequence) }
  }
}

/** Strategies employed for claiming the sequence of {@link AbstractEntry}s in the {@link RingBuffer} by producers.
 */
trait ClaimStrategy {
  /** Claim the next sequence index in the {@link RingBuffer} and increment.
   *
   *  @return the {@link AbstractEntry} index to be used for the producer.
   */
  def incrementAndGet(): Long

  /** Increment by a delta and get the result.
   *
   *  @param delta to increment by.
   *  @return the result after incrementing.
   */
  def incrementAndGet(delta: Int): Long

  /** Set the current sequence value for claiming {@link AbstractEntry} in the {@link RingBuffer}
   *
   *  @param sequence to be set as the current value.
   */
  def sequence_(sequence: Long)
}
