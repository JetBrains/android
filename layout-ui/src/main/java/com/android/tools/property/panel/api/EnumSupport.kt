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
package com.android.tools.property.panel.api

import javax.swing.ListCellRenderer

/**
 * Support for enumerated values.
 *
 * These values will be displayed in the dropdown of a ComboBox or DropDown control.
 */
interface EnumSupport {
  /**
   * The values to display in the enum control.
   */
  val values: List<EnumValue>

  /**
   * A [ListCellRenderer] for customizing the display of each value.
   */
  val renderer: ListCellRenderer<EnumValue>
    get() = EnumValue.DEFAULT_RENDERER

  /**
   * Create an [EnumValue] from an initial string value.
   *
   * This is used by DropDown controls for showing the initial value without asking for all possible enum values.
   */
  fun createValue(stringValue: String): EnumValue = EnumValue.item(stringValue)

  /**
   * A simple [EnumSupport] implementation where the values and renderer is specified up front.
   */
  private class SimpleEnumSupport(override val values: List<EnumValue>) : EnumSupport

  companion object {

    /** Creates an EnumSupport with the values specified */
    fun simple(values: List<EnumValue>): EnumSupport = SimpleEnumSupport(values)

    /** Creates an EnumSupport with the values specified as strings */
    fun simple(vararg values: String): EnumSupport = SimpleEnumSupport(convert(values))

    private fun convert(values: Array<out String>): List<EnumValue> {
      if (values.isEmpty()) {
        return emptyList()
      }
      return values.map { EnumValue.item(it) }.toList()
    }
  }
}
