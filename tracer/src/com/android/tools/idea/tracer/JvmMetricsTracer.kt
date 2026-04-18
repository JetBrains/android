/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.tracer

import androidx.tracing.Tracer
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.OperatingSystemMXBean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class JvmMetricsTracer(val scope: CoroutineScope, val tracer: () -> Tracer) {
  private val collectors = listOf(HeapMetricsCollector(), GcMetricsCollector(), CpuMetricsCollector())
  private var job: Job? = null

  @Synchronized
  fun setTracingEnabled(en: Boolean) {
    if ((job != null) == en) return

    job =
      if (en) {
        scope.launch {
          // Arbitrary time period of 1 seconds
          collectPeriodically(period = 1.seconds, collectors = collectors)
        }
      } else {
        job?.cancel()
        null
      }
  }

  private suspend fun collectPeriodically(period: Duration, collectors: List<JmxMetricsCollector>) {
    while (currentCoroutineContext().isActive) {
      collectors.forEach { it.record(tracer()) }
      delay(period)
    }
  }
}

private const val CATEGORY_JVM_MEMORY = "jvm.memory"
private const val CATEGORY_JVM_GC = "jvm.gc"
private const val CATEGORY_JVM_CPU = "jvm.cpu"
private const val CATEGORY_JVM_SYSTEM = "jvm.system"

private interface JmxMetricsCollector {
  fun record(tracer: Tracer)
}

internal class HeapMetricsCollector(private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()) : JmxMetricsCollector {
  private val metrics =
    listOf(
      MemoryBeanMetric(CATEGORY_JVM_MEMORY, "heap_used_bytes") { it.heapMemoryUsage.used },
      MemoryBeanMetric(CATEGORY_JVM_MEMORY, "non_heap_used_bytes") { it.nonHeapMemoryUsage.used },
    )

  override fun record(tracer: Tracer) {
    metrics.forEach { it.record(tracer, memoryBean) }
  }
}

internal class GcMetricsCollector(private val gcBeans: List<GarbageCollectorMXBean> = ManagementFactory.getGarbageCollectorMXBeans()) :
  JmxMetricsCollector {
  private val deltaCollectionCountMetric = DeltaGcMetric(CATEGORY_JVM_GC, "gc_collection_count_delta") { counts, _ -> counts }
  private val deltaCollectionTimeMetric = DeltaGcMetric(CATEGORY_JVM_GC, "gc_collection_time_ms_delta") { _, timeMs -> timeMs }

  override fun record(tracer: Tracer) {
    val totals =
      gcBeans.fold(GcTotals()) { totals, bean ->
        totals.copy(
          collectionCount = totals.collectionCount + bean.collectionCount.coerceAtLeast(0),
          collectionTimeMs = totals.collectionTimeMs + bean.collectionTime.coerceAtLeast(0),
        )
      }

    deltaCollectionCountMetric.record(tracer, totals)
    deltaCollectionTimeMetric.record(tracer, totals)
  }
}

internal class CpuMetricsCollector(private val operatingSystemBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()) :
  JmxMetricsCollector {
  private val metrics =
    listOf(
      OperatingSystemMetric(CATEGORY_JVM_CPU, "os.system_cpu_load") { bean -> bean.cpuLoad },
      OperatingSystemMetric(CATEGORY_JVM_SYSTEM, "os.system_load_average") { bean -> bean.systemLoadAverage },
      OperatingSystemMetric(CATEGORY_JVM_CPU, "os.process_cpu_load") { bean -> bean.processCpuLoad },
    )

  override fun record(tracer: Tracer) {
    val osBean = operatingSystemBean as? com.sun.management.OperatingSystemMXBean ?: return
    metrics.forEach { it.record(tracer, osBean) }
  }
}

private abstract class Metric<T>(private val category: String, private val name: String) {
  protected fun record(tracer: Tracer, value: Long?) {
    value?.let { tracer.counter(category, name).setValue(it) }
  }

  protected fun record(tracer: Tracer, value: Double?) {
    value?.let { tracer.counter(category, name).setValue(it) }
  }
}

private class MemoryBeanMetric(category: String, name: String, private val valueProvider: (MemoryMXBean) -> Long) :
  Metric<MemoryMXBean>(category, name) {
  fun record(tracer: Tracer, memoryBean: MemoryMXBean) {
    record(tracer, valueProvider(memoryBean))
  }
}

private class DeltaGcMetric(
  category: String,
  name: String,
  private val valueProvider: (collectionCount: Long, collectionTimeMs: Long) -> Long,
) : Metric<GcTotals>(category, name) {
  private var previousValue: Long? = null

  fun record(tracer: Tracer, totals: GcTotals) {
    val currentValue = valueProvider(totals.collectionCount, totals.collectionTimeMs)
    record(tracer, previousValue?.let { currentValue - it })
    previousValue = currentValue
  }
}

private class OperatingSystemMetric(
  category: String,
  name: String,
  private val valueProvider: (com.sun.management.OperatingSystemMXBean) -> Number?,
) : Metric<com.sun.management.OperatingSystemMXBean>(category, name) {
  fun record(tracer: Tracer, operatingSystemBean: com.sun.management.OperatingSystemMXBean) {
    when (val value = valueProvider(operatingSystemBean)) {
      is Long -> record(tracer, value)
      is Double -> record(tracer, value)
    }
  }
}

private data class GcTotals(val collectionCount: Long = 0, val collectionTimeMs: Long = 0)
