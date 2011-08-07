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

/** Used by the {@link BatchHandler} to signal when it has completed consuming a given sequence.
 * 
 *  NOTE: This was a non-static inner class of the BatchConsumer type, but Scala doesn't support those.  
 */
class SequenceTrackerCallback[A <: AbstractEntry](batchConsumer: BatchConsumer[A]) {
  /** Notify that the handler has consumed up to a given sequence.
   *
   *  @param sequence that has been consumed.
   */
  def onCompleted(sequence: Long) { batchConsumer.sequence_(sequence) }
}
