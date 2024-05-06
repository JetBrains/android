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
package com.android.tools.rendering

import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsRead

/** Class to record stats from a render result. */
data class RenderResultStats
constructor(
  /** Inflate duration in ms or -1 if unknown. */
  val inflateDurationMs: Long = -1,
  /** Render duration in ms or -1 if unknown. */
  val renderDurationMs: Long = -1,
  /** Classes loaded or -1 if unknown. */
  val classesFound: Long = -1,
  /** Total class loading duration of -1 if unknown. */
  val totalClassLoadDurationMs: Long = -1,
  /** Total class rewrite duration of -1 if unknown. */
  val totalClassRewriteDurationMs: Long = -1
) {

  constructor(
    inflateDurationMs: Long = -1,
    renderDurationMs: Long = -1,
    classLoaderStats: ModuleClassLoaderDiagnosticsRead?
  ) : this(
    inflateDurationMs,
    renderDurationMs,
    classLoaderStats?.classesFound ?: -1,
    classLoaderStats?.accumulatedFindTimeMs ?: -1,
    classLoaderStats?.accumulatedRewriteTimeMs ?: -1
  )

  /** Total render time (inflate + render). */
  val totalRenderDurationMs: Long =
    if (inflateDurationMs != -1L || renderDurationMs != -1L) {
      inflateDurationMs.coerceAtLeast(0) + renderDurationMs.coerceAtLeast(0)
    } else -1

  fun combine(stats: RenderResultStats): RenderResultStats =
    RenderResultStats(
      inflateDurationMs = maxOf(inflateDurationMs, stats.inflateDurationMs),
      renderDurationMs = maxOf(renderDurationMs, stats.renderDurationMs),
      classesFound = maxOf(classesFound, stats.classesFound),
      totalClassLoadDurationMs = maxOf(totalClassLoadDurationMs, stats.totalClassLoadDurationMs),
      totalClassRewriteDurationMs =
        maxOf(totalClassRewriteDurationMs, stats.totalClassRewriteDurationMs)
    )

  companion object {
    @JvmStatic val EMPTY = RenderResultStats()
  }
}
