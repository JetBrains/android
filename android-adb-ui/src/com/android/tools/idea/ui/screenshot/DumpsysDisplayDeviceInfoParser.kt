/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.screenshot

import java.awt.Dimension

/** Extracts active physical display information from output of `dumpsys display`. */
object DumpsysDisplayDeviceInfoParser {

  /** Parses `dumpsys display` output and returns parameters of active (state ON) physical displays. */
  fun getActiveDisplays(dumpsysOutput: String): List<DisplayDeviceInfo> {
    var match = Regex("\\s+DisplayDeviceInfo\\{", RegexOption.MULTILINE).find(dumpsysOutput)
    val result = mutableListOf<DisplayDeviceInfo>()
    val stateOnRegex = Regex("\\Wstate ON\\W")
    val logicalIdRegex = Regex("\\WmCurrentLayerStack=(\\d+)")
    val physicalIdRegex = Regex("\\WmPhysicalDisplayId=(\\d+)")
    val sizeRegex = Regex("\\W(\\d+) x (\\d+)\\W")
    val orientationRegex = Regex("\\Wrotation (\\d+)\\W")
    val densityRegex = Regex("\\Wdensity (\\d+)\\W")
    val isRoundRegex = Regex("\\WFLAG_ROUND\\W")
    while (match != null) {
      val startOffset = match.range.first
      val endOfMatch = match.range.last + 1
      match = match.next()
      var endOffset = match?.range?.first ?: dumpsysOutput.indexOf("\n\n", endOfMatch)
      if (endOffset < 0) {
        endOffset = dumpsysOutput.length
      }
      val section = dumpsysOutput.subSequence(startOffset, endOffset)
      if (!section.contains(stateOnRegex)) {
        continue
      }
      try {
        val logicalId = logicalIdRegex.find(section)?.groupValues?.get(1)?.toInt() ?: continue
        val physicalId = physicalIdRegex.find(section)?.groupValues?.get(1)?.toLong() ?: continue
        val size = sizeRegex.find(section)?.destructured?.let { (width, height) -> Dimension(width.toInt(), height.toInt()) } ?: Dimension()
        val orientationQuadrants = orientationRegex.find(section)?.groupValues?.get(1)?.toInt() ?: 0
        val density: Int = densityRegex.find(section)?.groupValues?.get(1)?.toInt() ?: 0
        val isRound = section.contains(isRoundRegex)
        result.add(DisplayDeviceInfo(logicalId, physicalId, size, orientationQuadrants, density, isRound))
      }
      catch (_: NumberFormatException) {
        continue
      }
    }
    return result
  }
}

data class DisplayDeviceInfo(
  val logicalId: Int,
  val physicalId: Long,
  val size: Dimension,
  val orientationQuadrants: Int,
  val density: Int,
  val isRound: Boolean,
)