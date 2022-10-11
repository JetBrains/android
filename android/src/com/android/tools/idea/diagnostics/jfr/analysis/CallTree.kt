/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.jfr.analysis

class CallTree(val name: String) {
  var sampleCount: Int = 0
  var truncatedSampleCount = 0
  var time: Long = 0
  private val children = mutableListOf<CallTree>()

  fun addStacktrace(stacktrace: List<String>, time: Long) {
    sampleCount++
    this.time += time
    var matchingChild = children.find { it.name == stacktrace.last() }
    if (matchingChild == null) {
      matchingChild = CallTree(stacktrace.last())
      children.add(matchingChild)
    }
    if (stacktrace.size > 1) {
      matchingChild.addStacktrace(stacktrace.subList(0, stacktrace.size - 1), time)
    } else {
      matchingChild.sampleCount++
      matchingChild.time += time
    }
  }

  fun sort() {
    children.sortByDescending { ch -> ch.time }
    children.forEach { it.sort() }
  }

  // this assumes sort() has been called
  override fun toString(): String = buildString {
    if (children.size > 1 && children[1].time > MIN_TIME_CUTOFF_MS) {
      children.forEach { c -> if (c.time > MIN_TIME_CUTOFF_MS) append(c.toString(1, true))}
    } else if (children.isNotEmpty()) {
      if (children[0].time > MIN_TIME_CUTOFF_MS) append(children[0].toString(0, false))
    }
  }

  private fun toString(depth: Int, branch: Boolean): String = buildString {
    val indent = depth - if (branch) 1 else 0
    for (i in 0 until indent) append("  ")
    if (branch) append("+ ")
    appendln("$name [${time}ms] ($sampleCount)")
    if (children.size > 1 && children[1].time > MIN_TIME_CUTOFF_MS) {
      children.forEach { c -> if (c.time > MIN_TIME_CUTOFF_MS) append(c.toString(depth+1, true))}
    } else if (children.isNotEmpty()) {
      if (children[0].time > MIN_TIME_CUTOFF_MS) append(children[0].toString(depth, false))
    }
  }

  fun numNodesAboveCutoff(): Int = if (time < MIN_TIME_CUTOFF_MS) 0 else 1 + children.sumOf { it.numNodesAboveCutoff() }

  companion object {
    private const val MIN_TIME_CUTOFF_MS = 200
  }
}