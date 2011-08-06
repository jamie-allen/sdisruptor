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

/** Set of common functions used by the Disruptor
 */
object Util {
  /** Calculate the next power of 2, greater than or equal to x.<p>
   *  From Hacker's Delight, Chapter 3, Harry S. Warren Jr.
   *
   *  @param x Value to round up
   *  @return The next power of 2 from x inclusive
   */
  def ceilingNextPowerOfTwo(x: Int) = 1 << (32 - Integer.numberOfLeadingZeros(x - 1))

  /** Get the minimum sequence from an array of {@link Consumer}s.
   *
   *  @param consumers to compare.
   *  @return the minimum sequence found or Long.MAX_VALUE if the array is empty.
   */
  def getMinimumSequence(consumers: Array[Consumer]) = {
      var minimum = Long.MaxValue

      for (consumer <- consumers) {
          val sequence = consumer.sequence
          minimum = if (minimum < sequence) minimum else sequence
      }

      minimum
  }
}
