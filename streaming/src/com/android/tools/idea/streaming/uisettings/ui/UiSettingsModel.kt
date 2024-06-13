/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.tools.idea.streaming.uisettings.binding.DefaultTwoWayProperty
import com.android.tools.idea.streaming.uisettings.binding.ReadOnlyProperty
import com.android.tools.idea.streaming.uisettings.binding.TwoWayProperty
import com.android.tools.idea.streaming.uisettings.data.AppLanguage
import java.awt.Dimension
import kotlin.math.abs

/**
 * Give 7 choices for font scales. A [percent] of 100 is the normal font scale.
 */
internal enum class FontScale(val percent: Int) {
  SMALL(85),
  NORMAL(100),
  LARGE_115(115),
  LARGE_130(130),
  LARGE_150(150),  // Added in API 34
  LARGE_180(180),  // Added in API 34
  LARGE_200(200);  // Added in API 34

  companion object {
    val scaleMap = FontScale.entries.map { it.percent }
  }
}

/**
 * Give 6 choices for wear devices.
 */
internal enum class WearFontScale(val percent: Int) {
  SMALL(94),
  NORMAL(100),
  MEDIUM(106),
  LARGE(112),
  LARGER(118),
  LARGEST(124);

  companion object {
    val scaleMap = WearFontScale.entries.map { it.percent }
  }
}

/**
 * A model for the [UiSettingsPanel] with bindable properties for getting and setting various Android settings.
 */
internal class UiSettingsModel(screenSize: Dimension, physicalDensity: Int, api: Int, private val isWear: Boolean = false) {
  private val densities = GoogleDensityRange.computeDensityRange(screenSize, physicalDensity)

  val inDarkMode: TwoWayProperty<Boolean> = DefaultTwoWayProperty(false)
  val gestureOverlayInstalled: ReadOnlyProperty<Boolean> = DefaultTwoWayProperty(false)
  val gestureNavigation: TwoWayProperty<Boolean> = DefaultTwoWayProperty(false)
  val appLanguage = UiComboBoxModel<AppLanguage>()
  val talkBackInstalled: ReadOnlyProperty<Boolean> = DefaultTwoWayProperty(false)
  val talkBackOn: TwoWayProperty<Boolean> = DefaultTwoWayProperty(false)
  val selectToSpeakOn: TwoWayProperty<Boolean> = DefaultTwoWayProperty(false)
  val fontScaleInPercent: TwoWayProperty<Int> = DefaultTwoWayProperty(100)
  val fontScaleSettable: ReadOnlyProperty<Boolean> = DefaultTwoWayProperty(true)
  val fontScaleIndex: TwoWayProperty<Int> = fontScaleInPercent.createMappedProperty(::toFontScaleIndex, ::toFontScaleInPercent)
  val fontScaleMaxIndex: ReadOnlyProperty<Int> = DefaultTwoWayProperty(numberOfFontScales(api) - 1)
  val screenDensity: TwoWayProperty<Int> = DefaultTwoWayProperty(physicalDensity)
  val screenDensitySettable: ReadOnlyProperty<Boolean> = DefaultTwoWayProperty(true)
  val screenDensityIndex: TwoWayProperty<Int> = screenDensity.createMappedProperty(::toDensityIndex, ::toDensityFromIndex)
  val screenDensityMaxIndex: ReadOnlyProperty<Int> = DefaultTwoWayProperty(densities.size - 1)
  val debugLayout:  TwoWayProperty<Boolean> = DefaultTwoWayProperty(false)
  val differentFromDefault: ReadOnlyProperty<Boolean> = DefaultTwoWayProperty(false)
  var resetAction: () -> Unit = {}

  /**
   * The font scale settings for wear has 6 values, API 33 has 4 values, and for API 34+ there are 7 possible values.
   * See [FontScale]
   */
  private fun numberOfFontScales(api: Int): Int = when {
    isWear -> WearFontScale.entries.size
    api == 33 -> 4
    else -> FontScale.entries.size
  }

  /**
   * The scaleMap for the current device type.
   */
  private val scaleMap: List<Int>
    get() = if (isWear) WearFontScale.scaleMap else FontScale.scaleMap

  private fun toFontScaleInPercent(fontIndex: Int): Int =
    scaleMap[fontIndex.coerceIn(0, fontScaleMaxIndex.value)]

  private fun toFontScaleIndex(percent: Int): Int =
    scaleMap.withIndex().minBy { (_,value) -> abs(value - percent) }.index

  private fun toDensityFromIndex(densityIndex: Int): Int =
    densities[densityIndex.coerceIn(0, screenDensityMaxIndex.value)]

  private fun toDensityIndex(density: Int): Int =
    densities.indexOf(densities.minBy { abs(it - density) })
}
