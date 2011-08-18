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

/** Used to record the batch of sequences claimed in a {@link RingBuffer}.
 */
class SequenceBatch(val size: Int) {
  private var _end = -1L

  /** Get the end sequence of a batch.
   *
   *  @return the end sequence in a batch
   */
  def end = _end

  /** Set the end of the batch sequence.  To be used by the {@link ProducerBarrier}.
   *
   *  @param end sequence in the batch.
   */
  def end_(end: Long) { _end = end }

  /** Get the starting sequence for a batch.
   *
   *  @return the starting sequence of a batch.
   */
  def getStart = _end - (size - 1L)
}
