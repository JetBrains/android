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
package org.jetbrains.android.uipreview

import org.jetbrains.annotations.VisibleForTesting
import java.util.PriorityQueue
import java.util.concurrent.atomic.LongAdder

/**
 * Interface for reading [ModuleClassLoader] stats.
 */
interface ModuleClassLoaderDiagnosticsRead {
  /**
   * The total number of classes loaded so far by the [ModuleClassLoader].
   */
  val classesFound: Long

  /**
   * The total time used finding classes by the [ModuleClassLoader].
   */
  val accumulatedFindTimeMs: Long

  /**
   * The total time used rewriting classes by the [ModuleClassLoader].
   */
  val accumulatedRewriteTimeMs: Long
}

/**
 * Interface for writing [ModuleClassLoader] stats.
 */
interface ModuleClassLoaderDiagnosticsWrite: ModuleClassLoaderDiagnosticsRead {
  fun classLoaded(fqn: String, timeMs: Long)
  fun classFound(fqn: String, timeMs: Long)
  fun classRewritten(fqn: String, length: Int, timeMs: Long)
}

/**
 * Nop implementation of the stats, to use in production.
 */
@VisibleForTesting
object NopModuleClassLoadedDiagnostics : ModuleClassLoaderDiagnosticsWrite {
  override fun classLoaded(fqn: String, timeMs: Long) {}
  override fun classFound(fqn: String, timeMs: Long) {}
  override fun classRewritten(fqn: String, length: Int, timeMs: Long) {}
  override val classesFound: Long = 0
  override val accumulatedFindTimeMs: Long = 0
  override val accumulatedRewriteTimeMs: Long = 0
}


/**
 * Implementation that records and saves the loading times and counts for classes.
 */
@VisibleForTesting
class ModuleClassLoadedDiagnosticsImpl : ModuleClassLoaderDiagnosticsWrite {
  /**
   * A single class find report with the name and time.
   */
  private data class ClassFoundReport(val fqn: String, val timeMs: Long)

  /** Captures the total time of the [ModuleClassLoader#loadClass] calls. */
  private val totalLoadTimeMs = LongAdder()
  /** Captures the total time of the c calls. */
  private val totalFindTimeMs = LongAdder()
  /** Counts the total number of classes found. */
  private val totalClassesFound = LongAdder()
  /** Counts the total time spent rewriting classes in this class loader. */
  private val totalRewriteTimeMs = LongAdder()
  /** Keeps the slowest classes by [ModuleClassLoader#loadClass] found time. */
  private val foundClasses = PriorityQueue<ClassFoundReport>(100, Comparator.comparing { it.timeMs })

  override fun classLoaded(fqn: String, timeMs: Long) {
    totalLoadTimeMs.add(timeMs)
  }
  override fun classFound(fqn: String, timeMs: Long) {
    totalFindTimeMs.add(timeMs)
    totalClassesFound.increment()
    foundClasses.add(ClassFoundReport(fqn, timeMs))
  }

  override fun classRewritten(fqn: String, length: Int, timeMs: Long) {
    totalRewriteTimeMs.add(timeMs)
  }

  override val classesFound: Long
    get() = totalClassesFound.sum()
  override val accumulatedFindTimeMs: Long
    get() = totalFindTimeMs.sum()
  override val accumulatedRewriteTimeMs: Long
    get() = totalRewriteTimeMs.sum()
}
