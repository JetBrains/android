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
package com.android.tools.idea.diagnostics.crash

import java.util.ArrayDeque

class LogBuffer(private val maxLines: Int) {

  private val deque = ArrayDeque<String>(maxLines)

  fun addEntry(entry: String) {
    synchronized(deque) {
      while (deque.size >= maxLines) {
        deque.removeFirst()
      }
      deque.addLast(entry)
    }
  }

  fun clear() {
    synchronized(deque) {
      deque.clear()
    }
  }

  fun getLog(): String {
    val sb = StringBuilder()
    synchronized(deque) {
      deque.forEach { sb.append(it).append('\n') }
    }
    return sb.toString()
  }

  fun getLogAndClear(): String {
    val result: String
    synchronized(deque) {
      result = getLog()
      clear()
    }
    return result
  }

}
