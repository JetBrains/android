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
package com.android.tools.idea.layoutinspector.properties

import com.android.resources.Density
import com.intellij.ide.util.PropertiesComponent

enum class DimensionUnits {
  PIXELS,
  DP,
}

private const val DIMENSION_UNITS = "live.layout.inspector.attr.dimension.unit"
private val DEFAULT_VALUE = DimensionUnits.DP

/** Global properties settings. */
object PropertiesSettings {

  /** The units to be used for all attributes with dimension values. */
  var dimensionUnits: DimensionUnits = initialDimensionUnitsValue()
    set(value) {
      field = value
      PropertiesComponent.getInstance().setValue(DIMENSION_UNITS, value.name, DEFAULT_VALUE.name)
    }

  /** The dpi of the device we are currently inspecting. */
  var dpi: Int = Density.DEFAULT_DENSITY

  private fun initialDimensionUnitsValue(): DimensionUnits {
    val strValue = PropertiesComponent.getInstance().getValue(DIMENSION_UNITS)
    try {
      return strValue?.let { DimensionUnits.valueOf(it) } ?: DEFAULT_VALUE
    } catch (ex: Exception) {
      // ignore...
      return DEFAULT_VALUE
    }
  }
}
