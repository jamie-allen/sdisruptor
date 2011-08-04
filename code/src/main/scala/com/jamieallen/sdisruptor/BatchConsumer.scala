/*
 * Copyright 2011 LMAX Ltd., modified by Jamie Allen
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

import _root_.com.lmax.disruptor.FatalExceptionHandler
import _root_.com.lmax.disruptor.ConsumerBarrier
import _root_.com.lmax.disruptor.BatchHandler
import _root_.com.lmax.disruptor.SequenceTrackingHandler
import _root_.com.lmax.disruptor.LifecycleAware
import _root_.com.lmax.disruptor.ExceptionHandler
import _root_.com.lmax.disruptor.AlertException

/** Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 *  and delegating the available {@link AbstractEntry}s to a {@link BatchHandler}.
 *
 *  If the {@link BatchHandler} also implements {@link LifecycleAware} it will be notified just after the thread
 *  is started and just before the thread is shutdown.
 *
 *  @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
class BatchConsumer[T <: AbstractEntry](consumerBarrier: ConsumerBarrier[T], handler: BatchHandler[T])  extends Consumer {
  var p1, p2, p3, p4, p5, p6, p7: Long  // cache line padding
  var p8, p9, p10, p11, p12, p13, p14: Long // cache line padding
  @volatile private var _sequence: Long = -1L // TODO: RingBuffer.INITIAL_CURSOR_VALUE;

  private var _exceptionHandler: ExceptionHandler = new FatalExceptionHandler()
  @volatile private var running = true

  if (handler.isInstanceOf[SequenceTrackingHandler[T]]) 
    handler.asInstanceOf[SequenceTrackingHandler[T]].setSequenceTrackerCallback(new SequenceTrackerCallback())

  override def sequence: Long = _sequence
  override def halt() {
    running = false
    consumerBarrier.alert
  }

  /**
   * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchConsumer}
   * (Jamie Allen: I'm allowing this to stay nullable for now)
   *
   * @param exceptionHandler to replace the existing exceptionHandler.
   */
  def exceptionHandler_(newExceptionHandler: ExceptionHandler) {
    if (null == newExceptionHandler) throw new NullPointerException();

    _exceptionHandler = newExceptionHandler
  }

  /** It is ok to have another thread rerun this method after a halt().
   */
  override def run {
    running = true;
    if (classOf[LifecycleAware].isAssignableFrom(handler.getClass())) handler.asInstanceOf[LifecycleAware].onStart()

    var entry = null.asInstanceOf[T]
    var nextSequence: Long = sequence + 1
    while (running) {
      try {
        val availableSequence = consumerBarrier.waitFor(nextSequence)
        while (nextSequence <= availableSequence) {
          entry = consumerBarrier.getEntry(nextSequence)
          handler.onAvailable(entry)
          nextSequence += 1
        }

        handler.onEndOfBatch();
        _sequence = entry.sequence
      }
      catch {
        case ae: AlertException => // Wake up from blocking wait and check if we should continue to run
        case ex: Exception => {
	        _exceptionHandler.handle(ex, entry.asInstanceOf[AbstractEntry])
	        _sequence = entry.sequence
	        nextSequence = entry.sequence + 1
	      }
      }
    }

    if (classOf[LifecycleAware].isAssignableFrom(handler.getClass())) handler.asInstanceOf[LifecycleAware].onShutdown()
  }

  /** Used by the {@link BatchHandler} to signal when it has completed consuming a given sequence.
   */
  class SequenceTrackerCallback {
    /** Notify that the handler has consumed up to a given sequence.
     *
     *  @param sequence that has been consumed.
     */
    def onCompleted(sequence: Long) { BatchConsumer.this._sequence = sequence }
  }
}