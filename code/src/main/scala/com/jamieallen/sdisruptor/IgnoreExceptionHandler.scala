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

import java.util.logging.Level;
import java.util.logging.Logger;

/** Convenience implementation of an exception handler that using standard JDK logging to log
 *  the exception as {@link Level}.INFO
 */
class IgnoreExceptionHandler(newLogger: Logger) extends ExceptionHandler {
  val logger: Logger = if (newLogger != null) newLogger 
  											else Logger.getLogger(classOf[FatalExceptionHandler].getName())

  override def handle(ex: Exception, currentEntry: AbstractEntry) { 
    logger.log(Level.INFO, "Exception processing: " + currentEntry, ex);
  }
}
