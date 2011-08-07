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

/** EntryConsumers waitFor {@link AbstractEntry}s to become available for consumption from the {@link RingBuffer}
 */
trait Consumer extends Runnable {
  /** Get the sequence up to which this Consumer has consumed {@link AbstractEntry}s
   *
   *  @return the sequence of the last consumed {@link AbstractEntry}
   */
  def sequence: Long
  def sequence_(newSequence: Long)

  /** Signal that this Consumer should stop when it has finished consuming at the next clean break.
   *  It will call {@link ConsumerBarrier#alert()} to notify the thread to check status.
   */
  def halt()
}
