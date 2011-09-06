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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import scala.util.control.Breaks._

object WaitStrategy {
  val Blocking = "blocking"
  val BusySpin = "busySpin"
  val Yielding = "yielding"
  
	def newInstance(option: String): WaitStrategy = {
	  option match {
	    case Blocking => new BlockingStrategy
	    case BusySpin => new BusySpinStrategy
	    case Yielding => new YieldingStrategy
	  }
	}

  /** Blocking strategy that uses a lock and condition variable for {@link Consumer}s waiting on a barrier.
   *
   *  This strategy should be used when performance and low-latency are not as important as CPU resource.
   */
  class BlockingStrategy extends WaitStrategy {
    private val lock = new ReentrantLock()
    private val consumerNotifyCondition = lock.newCondition()

    override def waitFor[A <: AbstractEntry](consumers: Array[Consumer], ringBuffer: RingBuffer[A], barrier: ConsumerBarrier[A], sequence: Long) = {
      var availableSequence: Long = ringBuffer.cursor
      if (availableSequence < sequence) {
        lock.lock()
        try {
          availableSequence = ringBuffer.cursor
          while (availableSequence < sequence) {
            if (barrier.isAlerted) throw AlertException.alertException;
            consumerNotifyCondition.await()
            availableSequence = ringBuffer.cursor
          }
        }
        finally { lock.unlock() }
      }

      if (0 != consumers.length) {
        availableSequence = Util.getMinimumSequence(consumers)
        while (availableSequence < sequence) {
          if (barrier.isAlerted) throw AlertException.alertException
          availableSequence = Util.getMinimumSequence(consumers)
        }
      }

      availableSequence
    }

    override def waitFor[A <: AbstractEntry](consumers: Array[Consumer], ringBuffer: RingBuffer[A], barrier: ConsumerBarrier[A], sequence: Long, timeout: Long, units: TimeUnit) = {
      var availableSequence: Long = ringBuffer.cursor
      if (availableSequence < sequence) {
        lock.lock()
        try {
          availableSequence = ringBuffer.cursor
          breakable {
	          while (availableSequence < sequence) {
	            if (barrier.isAlerted) throw AlertException.alertException
	            if (!consumerNotifyCondition.await(timeout, units)) break
	            availableSequence = ringBuffer.cursor
	          }
          }
        }
        finally { lock.unlock() }
      }

      if (0 != consumers.length) {
        availableSequence = Util.getMinimumSequence(consumers)
        while (availableSequence < sequence) {
          if (barrier.isAlerted) throw AlertException.alertException
          availableSequence = Util.getMinimumSequence(consumers)
        }
      }

      availableSequence
    }

  	override def signalAll() {
      lock.lock();
      try { consumerNotifyCondition.signalAll() }
      finally { lock.unlock() }
    }
  }

  /** Optimised strategy can be used when there is a single producer thread claiming {@link AbstractEntry}s.
   */
  class BusySpinStrategy extends WaitStrategy {
  	override def waitFor[A <: AbstractEntry](consumers: Array[Consumer], ringBuffer: RingBuffer[A], barrier: ConsumerBarrier[A], sequence: Long) = {
      var availableSequence: Long = -1L

      if (0 == consumers.length) {
        availableSequence = ringBuffer.cursor
        while (availableSequence < sequence) {
          if (barrier.isAlerted) throw AlertException.alertException
          availableSequence = ringBuffer.cursor
        }
      }
      else {
        availableSequence = Util.getMinimumSequence(consumers)
    		while (availableSequence < sequence) {
    			if (barrier.isAlerted) throw AlertException.alertException
    			availableSequence = Util.getMinimumSequence(consumers)
        }
      }

      availableSequence
    }

    override def waitFor[A <: AbstractEntry](consumers: Array[Consumer], ringBuffer: RingBuffer[A], barrier: ConsumerBarrier[A], sequence: Long, timeout: Long, units: TimeUnit) = {
      val timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS)
      val currentTime = System.currentTimeMillis()
      var availableSequence: Long = -1L

      if (0 == consumers.length) {
        availableSequence = ringBuffer.cursor
        breakable {
	        while (availableSequence < sequence) {
	          if (barrier.isAlerted) throw AlertException.alertException
	          if (timeoutMs < (System.currentTimeMillis() - currentTime)) break
	          availableSequence = ringBuffer.cursor
	        }
        }
      }
      else {
        availableSequence = Util.getMinimumSequence(consumers)
        breakable {
	        while (availableSequence < sequence) {
	          if (barrier.isAlerted) throw AlertException.alertException
	          if (timeoutMs < (System.currentTimeMillis() - currentTime)) break
	          availableSequence = Util.getMinimumSequence(consumers)
	        }
        }
      }

      availableSequence
    }

  	override def signalAll() { }
  }

  /** Yielding strategy that uses a Thread.yield() for {@link Consumer}s waiting on a barrier.
   *
   *  This strategy is a good compromise between performance and CPU resource.
   */
  class YieldingStrategy extends WaitStrategy {
  	override def waitFor[A <: AbstractEntry](consumers: Array[Consumer], ringBuffer: RingBuffer[A], barrier: ConsumerBarrier[A], sequence: Long) = {
      var availableSequence: Long = -1L

      if (0 == consumers.length) {
        availableSequence = ringBuffer.cursor
        while (availableSequence < sequence) {
          if (barrier.isAlerted) throw AlertException.alertException

          Thread.`yield`()
          availableSequence = ringBuffer.cursor
        }
      }
      else {
        availableSequence = Util.getMinimumSequence(consumers)
        while (availableSequence < sequence) {
          if (barrier.isAlerted) throw AlertException.alertException

          Thread.`yield`()
          availableSequence = Util.getMinimumSequence(consumers)
        }
      }

      availableSequence
    }

    override def waitFor[A <: AbstractEntry](consumers: Array[Consumer], ringBuffer: RingBuffer[A], barrier: ConsumerBarrier[A], sequence: Long, timeout: Long, units: TimeUnit) = {
      val timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS)
      val currentTime = System.currentTimeMillis()
      var availableSequence: Long = -1L

      if (0 == consumers.length) {
        availableSequence = ringBuffer.cursor
        breakable {
	        while (availableSequence < sequence) {
	          if (barrier.isAlerted) throw AlertException.alertException
	
	          Thread.`yield`()
	          if (timeoutMs < (System.currentTimeMillis() - currentTime)) break
	          availableSequence = ringBuffer.cursor
	        }
        }
      }
      else {
      	availableSequence = Util.getMinimumSequence(consumers)
        breakable {
	        while (availableSequence < sequence) {
	          if (barrier.isAlerted) throw AlertException.alertException
	
	          Thread.`yield`()
	          if (timeoutMs < (System.currentTimeMillis() - currentTime)) break
	          availableSequence = Util.getMinimumSequence(consumers)
	        }
      	}
      }

      availableSequence
    }
    
  	override def signalAll() { }
  }
}

/** Strategy employed for making {@link Consumer}s wait on a {@link RingBuffer}.
 */
trait WaitStrategy {
  /** Wait for the given sequence to be available for consumption in a {@link RingBuffer}
   *
   *  @param consumers further back the chain that must advance first
   *  @param ringBuffer on which to wait.
   *  @param barrier the consumer is waiting on.
   *  @param sequence to be waited on.
   *  @return the sequence that is available which may be greater than the requested sequence.
   */
  def waitFor[A <: AbstractEntry](consumers: Array[Consumer], ringBuffer: RingBuffer[A], barrier: ConsumerBarrier[A], sequence: Long): Long

  /** Wait for the given sequence to be available for consumption in a {@link RingBuffer} with a timeout specified.
   *
   *  @param consumers further back the chain that must advance first
   *  @param ringBuffer on which to wait.
   *  @param barrier the consumer is waiting on.
   *  @param sequence to be waited on.
   *  @param timeout value to abort after.
   *  @param units of the timeout value.
   *  @return the sequence that is available which may be greater than the requested sequence.
   */
  def waitFor[A <: AbstractEntry](consumers: Array[Consumer], ringBuffer: RingBuffer[A], barrier: ConsumerBarrier[A], sequence: Long, timeout: Long, units: TimeUnit): Long

  /** Signal those waiting that the {@link RingBuffer} cursor has advanced.
   */
  def signalAll()
}
