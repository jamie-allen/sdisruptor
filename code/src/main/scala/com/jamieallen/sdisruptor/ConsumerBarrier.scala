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

import java.util.concurrent.TimeUnit

/** Coordination barrier for tracking the cursor for producers and sequence of
 *  dependent {@link Consumer}s for a {@link RingBuffer}
 *
 *  @param <T> {@link AbstractEntry} implementation stored in the {@link RingBuffer}
 */
trait ConsumerBarrier[T <: AbstractEntry] {
  /** Get the {@link AbstractEntry} for a given sequence from the underlying {@link RingBuffer}.
   *
   *  @param sequence of the {@link AbstractEntry} to get.
   *  @return the {@link AbstractEntry} for the sequence.
   */
  def getEntry(sequence: Long): T

  /** Wait for the given sequence to be available for consumption.
   *
   *  @param sequence to wait for
   *  @return the sequence up to which is available
   */
  def waitFor(sequence: Long): Long

  /** Wait for the given sequence to be available for consumption with a time out.
   *
   *  @param sequence to wait for
   *  @param timeout value
   *  @param units for the timeout value
   *  @return the sequence up to which is available
   */
  def waitFor(sequence: Long, timeout: Long, units: TimeUnit): Long

  /** Delegate a call to the {@link RingBuffer#getCursor()}
   *  @return value of the cursor for entries that have been published.
   */
  def getCursor: Long

  /** The current alert status for the barrier.
   *
   *  @return true if in alert otherwise false.
   */
  def isAlerted: Boolean

  /** Alert the consumers of a status change and stay in this status until cleared.
   */
  def alert()

  /** Clear the current alert status.
   */
  def clearAlert()
}
