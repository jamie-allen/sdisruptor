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

object AlertException {
  /** Pre-allocated exception to avoid garbage generation */
  val alertException = new AlertException()
}

/** Used to alert consumers waiting at a {@link ConsumerBarrier} of status changes.
 *  <P>
 *  It does not fill in a stack trace for performance reasons.
 */
class AlertException extends Exception {

  /** Overridden so the stack trace is not filled in for this exception for performance reasons.
   *
   *  @return this instance.
   */
  override def fillInStackTrace(): Throwable = this;
}
