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

/** Abstraction for claiming {@link AbstractEntry}s in a {@link RingBuffer} while tracking dependent {@link Consumer}s
 *
 *  @param <T> {@link AbstractEntry} implementation stored in the {@link RingBuffer}
 */
trait ProducerBarrier[T <: AbstractEntry] {
  /** Get the {@link AbstractEntry} for a given sequence from the underlying {@link RingBuffer}.
   *
   *  @param sequence of the {@link AbstractEntry} to get.
   *  @return the {@link AbstractEntry} for the sequence.
   */
  def entry(sequence: Long): T

  /** Delegate a call to the {@link RingBuffer#getCursor()}
   *
   *  @return value of the cursor for entries that have been published.
   */
  def cursor: Long

  /** Claim the next {@link AbstractEntry} in sequence for a producer on the {@link RingBuffer}
   *
   *  @return the claimed {@link AbstractEntry}
   */
  def nextEntry: T;

  /** Claim the next batch of {@link AbstractEntry}s in sequence.
   *
   *  @param sequenceBatch to be updated for the batch range.
   *  @return the updated sequenceBatch.
   */
  def nextEntries(sequenceBatch: SequenceBatch): SequenceBatch

  /** Commit an entry back to the {@link RingBuffer} to make it visible to {@link Consumer}s
   *  @param entry to be committed back to the {@link RingBuffer}
   */
  def commit(entry: T)

  /** Commit the batch of entries back to the {@link RingBuffer}.
   *
   *  @param sequenceBatch to be committed.
   */
  def commit(sequenceBatch: SequenceBatch)
}
