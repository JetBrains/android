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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.adtui.stdui.setColorAndAlpha
import com.android.tools.idea.common.diagnostics.NlDiagnosticsManager
import com.android.tools.idea.common.diagnostics.NlDiagnosticsRead
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.rendering.RenderService
import com.intellij.util.io.IOUtil.MiB
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

val FONT = JBUI.Fonts.create(Font.MONOSPACED, 9)
val PCT_FORMAT = DecimalFormat("###.##")
val LAST_RENDER_BACKGROUND = Color(0x10, 0x10, 0x10, 0x20)

const val MAX_TIME_BAR_LENGTH = 50
const val TIME_BAR_WIDTH = 6
const val TIME_BAR_SPACE = 2
const val TIME_BAR_COUNT = 20
const val TIME_BAR_BOX_WIDTH = TIME_BAR_COUNT * TIME_BAR_WIDTH + (TIME_BAR_COUNT - 1) * TIME_BAR_SPACE
const val MB = 1024*1024 // bytes

fun Graphics2D.drawMultilineString(lines: String, x: Int, y: Int): Int {
  var lineStart = 0
  val fontMetrics = this.fontMetrics
  lines.split('\n')
    .forEach { this.drawString(it, x, y + lineStart); lineStart +=  fontMetrics.height }

  return lineStart
}

private fun colorForRenderTime(renderTimeMs: Long): Color =
  when {
    renderTimeMs > 60 -> Color.RED
    renderTimeMs > 40 -> Color.YELLOW
    renderTimeMs > 20 -> Color.BLUE
    else -> Color.GREEN
  }


class DiagnosticsLayer(val surface: DesignSurface<*>) : Layer() {
  private fun getDiagnosticsString(diagnostics: NlDiagnosticsRead): String {
    val runtime = Runtime.getRuntime()
    val freeMemPct = runtime.freeMemory().toDouble() / runtime.totalMemory() * 100
    val lastRenderMs = diagnostics.lastRenders().takeLast(1).firstOrNull() ?: -1
    val poolStats = RenderService.getInstance(surface.project).sharedImagePool.stats

    val bucketStats = poolStats?.bucketStats?.joinToString("\n") {
      " (${it.minWidth}x${it.minHeight} s=${it.maxSize()}) " +
      "lastAccess=${TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - it.lastAccessTimeMs)}s ago " +
      "hits=${it.bucketHits()} misses=${it.bucketMisses()} wasFull=${it.bucketWasFull()} hadSpace=${it.imageWasReturned()}"
    } ?: ""

    return """
      |General
      | Mem avail     ${runtime.freeMemory() / MiB}MB ${PCT_FORMAT.format(freeMemPct)}%
      | 90% Render    ${PCT_FORMAT.format(diagnostics.renderTime(90))}ms
      | Last render   ${PCT_FORMAT.format(lastRenderMs)}ms / ${diagnostics.lastRenderImageSize() / MiB}MB
      |
      |Image pool
      | Allocated     ${(poolStats?.totalBytesAllocated() ?: -1) / MiB}MB
      | In use        ${(poolStats?.totalBytesInUse() ?: -1) / MiB}MB
      | Free          ${((poolStats?.totalBytesAllocated() ?: -1) - (poolStats?.totalBytesInUse() ?: 0)) / MiB}MB
      |
      |Buckets
      |${bucketStats}
    """.trimMargin()
  }

  override fun paint(graphics2D: Graphics2D) {
    val gc = graphics2D.create() as Graphics2D
    gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    gc.setColorAndAlpha(Color.BLUE)
    gc.font = FONT

    val clipBounds = gc.clipBounds
    val diagnostics = NlDiagnosticsManager.getReadInstance(surface)
    var startY = clipBounds.y + 20
    val startX = clipBounds.x + 20
    startY += gc.drawMultilineString(getDiagnosticsString(diagnostics), startX, startY)
    drawLastRenders(gc, diagnostics.lastRenders(), startX, startY)

    gc.dispose()
  }

  private fun drawLastRenders(g: Graphics2D, lastRenders: List<Long>, x: Int, startLine: Int) {
    g.color = LAST_RENDER_BACKGROUND
    g.fillRect(x, startLine, TIME_BAR_BOX_WIDTH, MAX_TIME_BAR_LENGTH)
    var startX = x
    lastRenders.takeLast(TIME_BAR_COUNT)
      .forEach {
        g.color = colorForRenderTime(it)
        val barLength = Math.min((it / 3).toInt(), MAX_TIME_BAR_LENGTH)
        g.fillRect(startX, startLine + (MAX_TIME_BAR_LENGTH - barLength), TIME_BAR_WIDTH, barLength)
        startX += TIME_BAR_WIDTH + TIME_BAR_SPACE
      }
  }
}