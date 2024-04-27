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
package com.android.tools.idea.projectsystem.gradle.sync

import java.lang.Long.max
import java.lang.management.ManagementFactory
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A counter that given a function can run it and measure its wall and CPU time and provide aggregate statistics after running functions
 * multiple times.
 *
 * NOTE: Measuring CPU time is relatively expensive operation, and it should not be used to measure performance of relatively simple code
 *       (like dictionary lookup) running millions of times in a loop.
 */
class Counter internal constructor(val name: String) {

  private val totalCpu = AtomicLong()
  private val totalWall = AtomicLong()
  private val maxCpu = AtomicLong()
  private val maxWall = AtomicLong()
  private val count = AtomicInteger()

  val totalCpuNanos: Long get() = totalCpu.get()
  val maxCpuNanos: Long get() = maxCpu.get()
  val totalWallNanos: Long get() = totalWall.get()
  val maxWallNanos: Long get() = maxWall.get()
  val totalCount: Int get() = count.get()

  // Resets the counter.
  // Note, the counters are reset individually, so that if a call to this method coincides with a call to [time] method
  // it may result in partially recorded results.
  fun reset() {
    totalCpu.set(0)
    maxCpu.set(0)
    totalWall.set(0)
    maxWall.set(0)
    count.set(0)
  }

  private inline fun <R> time(block: () -> R): R {
    val startWall = currentTimeNano
    val startCpu = currentThreadCpuTime
    return try {
      block()
    } finally {
      val deltaWall = currentTimeNano - startWall
      val deltaCpu = currentThreadCpuTime - startCpu
      totalCpu.addAndGet(deltaCpu)
      maxCpu.updateAndGet { max(it, deltaCpu) }
      totalWall.addAndGet(deltaWall)
      maxWall.updateAndGet { max(it, deltaWall) }
      count.incrementAndGet()
    }
  }

  override fun toString(): String = buildString {
    val totalCount = count.get()
    if (totalCount > 0) {
      val avgCpuMicros = (totalCpuNanos / totalCount) / 100 / 10.0
      val maxCpuMicros = maxCpuNanos / 100 / 10.0
      val totalCpuMillis = totalCpuNanos / 100_000 / 10.0

      val avgWallMicros = (totalWallNanos / totalCount) / 100 / 10.0
      val maxWallMicros = maxWallNanos / 100 / 10.0
      val totalWallMillis = totalWallNanos / 100_000 / 10.0

      append("Counter:     ", name, " \n")
      append("    Count:     ", totalCount, " \n")
      append("    TotalCpu:  ", totalCpuMillis.format(), "ms \n")
      append("    TotalWall: ", totalWallMillis.format(), "ms \n")
      appendLine()
      append("    AvgCpu:    ", avgCpuMicros.format(), "μs \n")
      append("    MaxCpu:    ", maxCpuMicros.format(), "μs \n")
      append("    AvgWall:   ", avgWallMicros.format(), "μs \n")
      append("    MaxWall:   ", maxWallMicros.format(), "μs \n")
      appendLine()
    }
  }

  operator fun <R> invoke (block: () -> R): R = time(block)
}

private val formatter: DecimalFormat = DecimalFormat("#,##0.00")
fun Double.format(): String = formatter.format(this).padStart(15)

private val threadMx = ManagementFactory.getThreadMXBean()
private val currentTimeNano: Long get() = System.nanoTime()
private val currentThreadCpuTime: Long get() = runCatching { threadMx.currentThreadCpuTime }.getOrDefault(0)