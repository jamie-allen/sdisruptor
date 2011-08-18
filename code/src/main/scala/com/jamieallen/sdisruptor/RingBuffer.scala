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

/** Ring based store of reusable entries containing the data representing an {@link AbstractEntry} being exchanged between producers and consumersToTrack.
 *
 *  @param <T> AbstractEntry implementation storing the data for sharing during exchange or parallel coordination of an event.
 *  @param entryFactory to create {@link AbstractEntry}s for filling the RingBuffer
 *  @param size of the RingBuffer that will be rounded up to the next power of 2
 *  @param claimStrategyOption threading strategy for producers claiming {@link AbstractEntry}s in the ring.
 *  @param waitStrategyOption waiting strategy employed by consumersToTrack waiting on {@link AbstractEntry}s becoming available.
 */
class RingBuffer[T <: AbstractEntry : ClassManifest](entryFactory: EntryFactory[T], 
    								size: Int,
                    var claimStrategyOption: Symbol,
                    var waitStrategyOption: Symbol) extends ProducerBarrier[T] {
  if (claimStrategyOption == null) claimStrategyOption = ClaimStrategy.MultiThreaded
  if (waitStrategyOption == null) waitStrategyOption = WaitStrategy.Blocking


  val p1, p2, p3, p4, p5, p6, p7: Long = -1L // cache line padding
  @volatile private var _cursor = -1L
  val p8, p9, p10, p11, p12, p13, p14: Long = -1L // cache line padding

  val sizeAsPowerOfTwo = Util.ceilingNextPowerOfTwo(size)
  val ringModMask = sizeAsPowerOfTwo - 1
  val entries: Array[T] = new Array[T](sizeAsPowerOfTwo)
  
  var lastTrackedConsumerMin = -1L
  var _consumersToTrack = new Array[Consumer](0)

  var claimStrategy: ClaimStrategy = ClaimStrategy.newInstance(claimStrategyOption)
  var waitStrategy: WaitStrategy = WaitStrategy.newInstance(waitStrategyOption)

  fill(entryFactory);

  /** Set the consumersToTrack that will be tracked to prevent the ring wrapping.
   *
   *  This method must be called prior to claiming entries in the RingBuffer otherwise
   *  a NullPointerException will be thrown.
   *
   *  @param consumers to be tracked.
   */
  def consumersToTrack_(consumers: Array[Consumer]) { _consumersToTrack = consumers }

  /** Create a {@link ConsumerBarrier} that gates on the RingBuffer and a list of {@link Consumer}s
   *
   *  @param consumersToTrack this barrier will track
   *  @return the barrier gated as required
   */
  def createConsumerBarrier(consumersToTrack: Array[Consumer]): ConsumerBarrier[T] = new ConsumerTrackingConsumerBarrier(consumersToTrack)

  /** The capacity of the RingBuffer to hold entries.
   *
   *  @return the size of the RingBuffer.
   */
  def capacity = entries.length

  /** Get the current sequence that producers have committed to the RingBuffer.
   *
   *  @return the current committed sequence.
   */
  def cursor(): Long = { _cursor }

  /** Get the {@link AbstractEntry} for a given sequence in the RingBuffer.
   *
   *  @param sequence for the {@link AbstractEntry}
   *  @return {@link AbstractEntry} for the sequence
   */
  def entry(sequence: Long): T = entries(sequence.asInstanceOf[Int] & ringModMask).asInstanceOf[T]

  override def nextEntry(): T = { 
    val sequence = claimStrategy.incrementAndGet()
    ensureConsumersAreInRange(sequence)

    val entry = entries(sequence.asInstanceOf[Int] & ringModMask)
    entry.sequence_(sequence)

    entry.asInstanceOf[T]
  }

  override def commit(entry: T) { commit(entry.sequence, 1) }

  override def nextEntries(sequenceBatch: SequenceBatch): SequenceBatch = {
    val sequence = claimStrategy.incrementAndGet(sequenceBatch.size)
    sequenceBatch.end_(sequence);
    ensureConsumersAreInRange(sequence);

    for (i <- sequenceBatch.getStart until sequenceBatch.end) {
      val entry = entries(i.asInstanceOf[Int] & ringModMask)
      entry.sequence_(i)
    }

    return sequenceBatch;
  }

  override def commit(sequenceBatch: SequenceBatch): Unit = { commit(sequenceBatch.end, sequenceBatch.size) }

  /** Claim a specific sequence in the {@link RingBuffer} when only one producer is involved.
   *
   *  @param sequence to be claimed.
   *  @return the claimed {@link AbstractEntry}
   */
  def claimEntryAtSequence(sequence: Long): T = {
    ensureConsumersAreInRange(sequence)
    val entry = entries(sequence.asInstanceOf[Int] & ringModMask)
    entry.sequence_(sequence)
    entry.asInstanceOf[T]
  }

  /** Commit an entry back to the {@link RingBuffer} to make it visible to {@link Consumer}s.
   *  Only use this method when forcing a sequence and you are sure only one producer exists.
   *  This will cause the {@link RingBuffer} to advance the {@link RingBuffer#getCursor()} to this sequence.
   *
   *  @param entry to be committed back to the {@link RingBuffer}
   */
  def commitWithForce(entry: T) {
    claimStrategy.sequence_(entry.sequence)
    _cursor = entry.sequence
    waitStrategy.signalAll()
  }

  private def ensureConsumersAreInRange(sequence: Long) {
    val wrapPoint = sequence - entries.length;
    while (wrapPoint > lastTrackedConsumerMin &&
           wrapPoint > Util.getMinimumSequence(_consumersToTrack)) {
      lastTrackedConsumerMin = Util.getMinimumSequence(_consumersToTrack);
      Thread.`yield`()
    }
  }

  private def commit(sequence: Long, batchSize: Long) {
    if (ClaimStrategy.MultiThreaded == claimStrategyOption) {
      val expectedSequence = sequence - batchSize
      var counter = 1000
      while (expectedSequence != cursor) {
        counter -= 1
        if (0 == counter) {
          counter = 1000
          Thread.`yield`()
        }
      }
    }

    _cursor = sequence
    waitStrategy.signalAll();
  }

  private def fill(entryFactory: EntryFactory[T]) { for (i <- 0 until entries.length) entries(i) = entryFactory.create() }

  /** ConsumerBarrier handed out for gating consumersToTrack of the RingBuffer and dependent {@link Consumer}(s)
   */
  private class ConsumerTrackingConsumerBarrier(consumers: Array[Consumer]) extends ConsumerBarrier[T] {
    @volatile private var alerted = false;

    override def getEntry(sequence: Long): T = { entries(sequence.asInstanceOf[Int] & ringModMask).asInstanceOf[T] }
    override def waitFor(sequence: Long): Long = { waitStrategy.waitFor(consumers, RingBuffer.this, this, sequence) }
    override def waitFor(sequence: Long, timeout: Long, units: TimeUnit): Long = { waitStrategy.waitFor(consumers, RingBuffer.this, this, sequence, timeout, units) }
    override def getCursor = cursor
    override def isAlerted = alerted
    override def alert() {
      alerted = true
      waitStrategy.signalAll()
    }
    override def clearAlert() { alerted = false }
  }
}
