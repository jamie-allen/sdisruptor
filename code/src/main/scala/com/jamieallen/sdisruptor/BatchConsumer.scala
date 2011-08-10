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

/** Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 *  and delegating the available {@link AbstractEntry}s to a {@link BatchHandler}.
 *
 *  If the {@link BatchHandler} also implements {@link LifecycleAware} it will be notified just after the thread
 *  is started and just before the thread is shutdown.
 *
 *  @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
class BatchConsumer[A <: AbstractEntry](consumerBarrier: ConsumerBarrier[A], handler: BatchHandler[A]) extends Consumer {
  val p1, p2, p3, p4, p5, p6, p7: Long = -1L  // cache line padding
  @volatile private var _sequence: Long = RingBuffer.InitialCursorValue
  val p8, p9, p10, p11, p12, p13, p14: Long = -1L // cache line padding

  private var _exceptionHandler: ExceptionHandler = new FatalExceptionHandler(null)
  @volatile private var running = true

  if (handler.isInstanceOf[SequenceTrackingHandler[A]])
    handler.asInstanceOf[SequenceTrackingHandler[A]].setSequenceTrackerCallback(new SequenceTrackerCallback(this))

  override def sequence: Long = _sequence
  override def sequence_(newSequence: Long) { _sequence = newSequence }

  override def halt() {
    running = false
    consumerBarrier.alert
  }

  /** Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchConsumer}
   *  (Jamie Allen: I'm allowing this to stay nullable for now)
   *
   *  @param exceptionHandler to replace the existing exceptionHandler.
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

    var entry = null.asInstanceOf[A]
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
}
