/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.resources.NightMode
import com.android.tools.idea.configurations.Configuration
import com.android.ide.common.resources.Locale
import com.android.utils.HtmlBuilder

fun Configuration.toTooltips() = StringBuilder()
  .append(device?.let { "${it.displayName}, " } ?: "")
  .append(target?.let { "API ${it.version.apiLevel}, " } ?: "")
  .append(deviceState?.orientation?.let { "${it.name}, " } ?: "")
  .append("${Locale.getLocaleLabel(locale, false)}, ")
  .append("$theme, ")
  .append("${uiMode.longDisplayValue}, ")
  .append(nightMode.longDisplayValue)
  .toString()

fun Configuration.toHtmlTooltip(): String {
  val tooltip = HtmlBuilder()
  val device = device
  if (device != null) {
    tooltip.addBold("Hardware").newline()
      .add("Device: ${device.displayName}").newline()
    val orientation = deviceState?.orientation ?: device.defaultState.orientation
    val pxSize = device.getScreenSize(orientation)
    val dpi = density.dpiValue
    pxSize?.let { tooltip.add("Dimensions: ${it.width * 160 / dpi} x ${it.height * 160 / dpi} dp").newline() }
    tooltip.add("Orientation: ${orientation.shortDisplayValue}").newline().newline()
  }
  tooltip.addBold("Display").newline()
  target?.let { tooltip.add("API: ${it.version.apiLevel}").newline() }
  tooltip.add("Locale: ${Locale.getLocaleLabel(locale, false)}").newline()
    .add("Theme: $theme").newline()
    .add("UI Mode: ${uiMode.shortDisplayValue}").newline()
  if (nightMode == NightMode.NIGHT) {
    tooltip.add("Night Mode: True")
  }
  else {
    tooltip.add("Night Mode: False")
  }
  return tooltip.html
}
