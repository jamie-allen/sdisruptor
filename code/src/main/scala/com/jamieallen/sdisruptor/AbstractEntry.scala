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

/** Base implementation that must be extended for {@link RingBuffer} entries.
 */
abstract class AbstractEntry {
  var _sequence: Long
  
  def sequence: Long = sequence
  def sequence_(newValue: Long) { _sequence = newValue }
}
