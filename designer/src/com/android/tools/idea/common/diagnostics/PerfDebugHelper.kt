/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.diagnostics

import com.intellij.openapi.diagnostic.Logger
import java.util.LinkedList

class PerfDebugHelper {

  val DEBUG = false

  val myStats = HashMap<String, LinkedList<Long>>()

  fun start(name: String) {
    if (!DEBUG) {
      return
    }

    val current = System.currentTimeMillis()
    var elapsedList = myStats[name]
    if (elapsedList == null) {
      elapsedList = LinkedList()
      myStats[name] = elapsedList
    }

    if (elapsedList.size % 2 != 0) {
      uneven(name)
      return
    }

    elapsedList.add(current)
  }

  fun end(name: String) {
    if (!DEBUG) {
      return
    }

    val current = System.currentTimeMillis()
    val list = myStats[name]
    if (list == null || list.size % 2 != 1) {
      uneven(name)
      return
    }

    list.add(current)
  }

  private fun uneven(name: String) {
    log("${name} has uneven start/end callsites. Unable to proceed.")
  }

  private fun log(msg: String) {
    Logger.getInstance(PerfDebugHelper::class.java).warn(msg)
  }

  fun avg(name: String): Long {
    if (!DEBUG) {
      return -1
    }

    val list = myStats[name]
    if (list == null || list.isEmpty() || list.size % 2 != 0) {
      uneven(name)
      return -1
    }

    val halfSize = list.size / 2
    var sum = 0L

    for (i in 0..(halfSize - 1) step 1) {
      val start = list[i*2]
      val end = list[i*2+1]
      sum += (end - start)
    }

    return sum / halfSize
  }

  fun print() {
    if (!DEBUG) {
      return
    }

    log("------")
    for (entry in myStats) {
      log("${entry.key} : ${avg(entry.key)}")
    }
    log("------")
  }
}