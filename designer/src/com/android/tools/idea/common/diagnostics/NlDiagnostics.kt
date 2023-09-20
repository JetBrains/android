/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags.NELE_RENDER_DIAGNOSTICS
import com.google.common.cache.CacheBuilder
import com.google.common.collect.EvictingQueue
import com.google.common.math.Quantiles
import java.util.concurrent.TimeUnit

/** Interface for reading the diagnose information */
interface NlDiagnosticsRead {
  /** Returns the [percentile] percentile for the render time */
  fun renderTime(percentile: Int): Long

  /** Returns the last renders time in ms */
  fun lastRenders(): List<Long>

  /** Returns the last render image size in bytes */
  fun lastRenderImageSize(): Long
}

/** Interface for the layout editor to provide diagnostics information */
interface NlDiagnosticsWrite {
  /** Record a render action with the time and the size of the generated image */
  fun recordRender(timeMs: Long, lastRenderSizeBytes: Long)
}

/** Implementation returned when diagnostics are disabled */
object NopNlDiagnosticsImpl : NlDiagnosticsRead, NlDiagnosticsWrite {
  override fun lastRenderImageSize(): Long = -1

  override fun renderTime(percentile: Int): Long = -1

  override fun recordRender(timeMs: Long, lastRenderSizeBytes: Long) {}

  override fun lastRenders(): List<Long> = emptyList()
}

private class NlDiagnosticsImpl : NlDiagnosticsRead, NlDiagnosticsWrite {
  private val lastRenderTimes = EvictingQueue.create<Long>(100)
  private var lastRenderImageBytes = -1L

  override fun lastRenders(): List<Long> = lastRenderTimes.toList()

  override fun lastRenderImageSize(): Long = lastRenderImageBytes

  override fun recordRender(timeMs: Long, lastRenderSizeBytes: Long) {
    lastRenderTimes.add(timeMs)
    lastRenderImageBytes = lastRenderSizeBytes
  }

  override fun renderTime(percentile: Int): Long =
    if (lastRenderTimes.size > 0)
      Quantiles.percentiles().index(percentile).compute(lastRenderTimes).toLong()
    else -1
}

/** Key for [NlDiagnosticsManager] to read/write from cache. */
interface NlDiagnosticKey

object NlDiagnosticsManager {
  private val cache =
    CacheBuilder.newBuilder()
      .weakKeys()
      .expireAfterAccess(5, TimeUnit.MINUTES)
      .build<NlDiagnosticKey, NlDiagnosticsImpl>()

  /** Returns the [NlDiagnosticsRead] instance associated to the given surface */
  @JvmStatic
  fun getReadInstance(key: NlDiagnosticKey?): NlDiagnosticsRead =
    if (key == null || !NELE_RENDER_DIAGNOSTICS.get()) {
      NopNlDiagnosticsImpl
    } else
      cache.get(key) {
        return@get NlDiagnosticsImpl()
      }

  /** Returns the [NlDiagnosticsWrite] instance associated to the given surface */
  @JvmStatic
  fun getWriteInstance(key: NlDiagnosticKey?): NlDiagnosticsWrite =
    if (key == null || !NELE_RENDER_DIAGNOSTICS.get()) {
      NopNlDiagnosticsImpl
    } else
      cache.get(key) {
        return@get NlDiagnosticsImpl()
      }
}
