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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.formatter.TimeFormatter
import java.text.DecimalFormat
import javax.swing.table.DefaultTableCellRenderer

/**
 * Renders duration in microseconds in human readable format.
 */
internal class DurationRenderer : DefaultTableCellRenderer() {
  override fun setValue(value: Any?) {
    val duration = value as Long
    text = TimeFormatter.getSingleUnitDurationString(duration)
  }
}

/**
 * Renders timestamp in microseconds in human readable format.
 */
internal class TimestampRenderer : DefaultTableCellRenderer() {
  override fun setValue(value: Any?) {
    val timestamp = value as Long
    text = TimeFormatter.getSemiSimplifiedClockString(timestamp)
  }
}

/**
 * Renders percentage number in human readable format.
 */
internal class PercentRenderer : DefaultTableCellRenderer() {
  private val percentFormatter = DecimalFormat("#.##%")

  override fun setValue(value: Any?) {
    val percentage = value as Double
    text = percentFormatter.format(percentage)
  }
}

/**
 * Renders an integer as String. May be used for consistent alignment.
 */
internal class IntegerAsStringTableCellRender : DefaultTableCellRenderer() {
  override fun setValue(value: Any?) {
    text = (value as Long).toString()
  }
}
