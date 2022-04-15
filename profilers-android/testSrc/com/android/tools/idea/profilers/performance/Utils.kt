/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.performance

import com.android.tools.perflogger.Benchmark
import java.lang.management.ManagementFactory
import kotlin.system.measureTimeMillis

fun getMemoryUsed(): Long {
  val memoryUsage = ManagementFactory.getMemoryMXBean()
  ensureGc()
  return memoryUsage.heapMemoryUsage.used
}

fun ensureGc() {
  repeat (10) { System.gc() }
  System.runFinalization()
  repeat (10) { ManagementFactory.getMemoryMXBean().gc() }
}

interface BenchmarkRunner {
  operator fun<A: Any> invoke(tag: String, work: () -> A): A
}

/**
 * Create a runner that runs each given (tagged) action in
 * a memory benchmark and a time benchmark.
 */
fun benchmarkMemoryAndTime(graphTitlePrefix: String,
                           legendTitleSuffix: String,
                           memUnit: MemoryUnit = MemoryUnit.MB,
                           timeUnit: TimeUnit = TimeUnit.MS): BenchmarkRunner {
  val logTime = makeLogger("$graphTitlePrefix Time (${timeUnit.title})", legendTitleSuffix)
  val logMem = makeLogger("$graphTitlePrefix Memory (${memUnit.title})", "$legendTitleSuffix-Used")
  return object : BenchmarkRunner {
    override fun <A: Any> invoke(tag: String, work: () -> A): A {
      lateinit var ans: A
      val beforeMem = getMemoryUsed()
      val elapsedMillis = measureTimeMillis {
        ans = work()
      }
      val afterMem = getMemoryUsed()
      logTime(tag, elapsedMillis / timeUnit.asMillis)
      logMem(tag, (afterMem - beforeMem) / memUnit.asBytes)
      return ans
    }
  }
}

private fun makeLogger(graphTitle: String, legendSuffix: String): (tag: String, number: Long) -> Unit {
  val bm = Benchmark.Builder(graphTitle).setProject("Android Studio Profilers").build()
  return { tag, number -> bm.log("$tag-$legendSuffix", number) }
}

enum class MemoryUnit(val title: String, val asBytes: Long) {
  B("b", 1),
  KB("kb", 1024),
  MB("mb", 1024 * 1024)
}

enum class TimeUnit(val title: String, val asMillis: Long) {
  MS("millis", 1),
  S("seconds", 1000)
}