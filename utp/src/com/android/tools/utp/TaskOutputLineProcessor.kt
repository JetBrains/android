/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.utp

import java.io.Closeable

/**
 * Processes text into newline-delimited segments for processing.
 *
 * @param lineProcessor an interface that determines how lines are processed
 */
class TaskOutputLineProcessor(private var lineProcessor: LineProcessor): Closeable {

  private val stringBuffer = StringBuffer()

  interface LineProcessor {
    /**
     * Process a single, complete line of output
     *
     * @param line the complete line of output, not including "\n"
     */
    fun processLine(line: String)
  }

  /**
   * Assemble provided text into lines to be processed
   */
  fun append(text: String) {
    stringBuffer.append(text)
    while (true) {
      var lineBreakIndex = -1
      var lineBreakLength = 0
      for (i in stringBuffer.indices) {
        val c = stringBuffer[i]
        if (c == '\r' || c == '\n') {
          lineBreakIndex = i
          lineBreakLength = 1
          if (c == '\r' && i + 1 < stringBuffer.length && stringBuffer[i + 1] == '\n') {
            ++lineBreakLength
          }
          break
        }
      }
      if (lineBreakIndex == -1) {
        return
      }
      val line = stringBuffer.substring(0, lineBreakIndex)
      stringBuffer.delete(0, lineBreakIndex + lineBreakLength)
      lineProcessor.processLine(line)
    }
  }

  override fun close() {
    // process any remaining text that did not end in "\n" when processor is closed
    if (stringBuffer.isNotEmpty()) {
      lineProcessor.processLine(stringBuffer.toString())
    }
  }
}