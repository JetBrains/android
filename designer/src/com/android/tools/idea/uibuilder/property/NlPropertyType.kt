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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants
import com.android.ide.common.resources.parseColor
import com.android.resources.ResourceType
import com.intellij.psi.util.PsiLiteralUtil
import java.util.EnumSet
import org.jetbrains.android.dom.converters.DimensionConverter

/** Types of a [NlPropertyItem]. */
enum class NlPropertyType {
  UNKNOWN,
  ANIM,
  ANIMATOR,
  ARRAY,
  BOOLEAN,
  CLASS_NAME,
  COLOR,
  COLOR_STATE_LIST,
  DIMENSION,
  DIMENSION_PIXEL, // Dimension in pixels (motion layout)
  DIMENSION_UNIT_LESS, // Dimension or unit less float (motion layout)
  DESTINATION,
  DRAWABLE,
  ENUM,
  FLAGS,
  FLOAT,
  FONT,
  FONT_SIZE,
  FRACTION,
  FRAGMENT,
  ID,
  ID_OR_STRING, // id or string (motion layout)
  INTEGER,
  INTERPOLATOR,
  LAYOUT,
  LIST,
  MENU,
  NAVIGATION,
  READONLY_STRING,
  STRING,
  STRING_ARRAY,
  STYLE,
  THREE_STATE_BOOLEAN,
  TEXT_APPEARANCE,
  XML;

  val resourceTypes: EnumSet<ResourceType>
    get() =
      when (this) {
        ANIM -> EnumSet.of(ResourceType.ANIM)
        ANIMATOR -> EnumSet.of(ResourceType.ANIMATOR, ResourceType.ANIM)
        ARRAY -> EnumSet.of(ResourceType.ARRAY)
        BOOLEAN -> EnumSet.of(ResourceType.BOOL)
        COLOR -> EnumSet.of(ResourceType.COLOR)
        COLOR_STATE_LIST -> EnumSet.of(ResourceType.COLOR)
        DESTINATION -> EnumSet.of(ResourceType.ID)
        DIMENSION_PIXEL,
        DIMENSION -> EnumSet.of(ResourceType.DIMEN)
        DRAWABLE -> EnumSet.of(ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP)
        FLAGS -> EnumSet.of(ResourceType.STRING)
        FLOAT -> EnumSet.of(ResourceType.DIMEN)
        FONT -> EnumSet.of(ResourceType.FONT)
        FRACTION -> EnumSet.of(ResourceType.FRACTION)
        FONT_SIZE -> EnumSet.of(ResourceType.DIMEN)
        ID -> EnumSet.of(ResourceType.ID)
        ID_OR_STRING -> EnumSet.of(ResourceType.ID, ResourceType.STRING)
        INTEGER -> EnumSet.of(ResourceType.INTEGER)
        INTERPOLATOR -> EnumSet.of(ResourceType.INTERPOLATOR)
        LAYOUT -> EnumSet.of(ResourceType.LAYOUT)
        LIST -> EnumSet.noneOf(ResourceType.ID.javaClass)
        MENU -> EnumSet.of(ResourceType.MENU)
        NAVIGATION -> EnumSet.of(ResourceType.NAVIGATION)
        READONLY_STRING -> EnumSet.noneOf(ResourceType.ID.javaClass)
        STRING -> EnumSet.of(ResourceType.STRING)
        STRING_ARRAY -> EnumSet.of(ResourceType.ARRAY)
        STYLE -> EnumSet.of(ResourceType.STYLE)
        TEXT_APPEARANCE -> EnumSet.of(ResourceType.STYLE)
        THREE_STATE_BOOLEAN -> EnumSet.of(ResourceType.BOOL)
        XML -> EnumSet.of(ResourceType.XML)
        else -> EnumSet.noneOf(ResourceType.BOOL.javaClass)
      }

  /**
   * Check the specified [literal] value and return the error if any or null.
   *
   * This method does not check resource values, theme values, and enum values. Those checks should
   * be made BEFORE calling this method.
   */
  fun validateLiteral(literal: String): String? {
    if (literal.isEmpty()) {
      return null
    }
    return when (this) {
      THREE_STATE_BOOLEAN,
      BOOLEAN ->
        error(literal != SdkConstants.VALUE_TRUE && literal != SdkConstants.VALUE_FALSE) {
          "Invalid bool value: '$literal'"
        }
      COLOR_STATE_LIST,
      COLOR,
      DRAWABLE -> error(parseColor(literal) == null) { "Invalid color value: '$literal'" }
      ENUM -> "Invalid value: '$literal'"
      FONT_SIZE,
      DIMENSION_PIXEL,
      DIMENSION ->
        error(DimensionConverter.INSTANCE.doFromString(literal) == null) {
          getDimensionError(literal)
        }
      DIMENSION_UNIT_LESS -> checkUnitLessDimension(literal)
      FLOAT -> error(PsiLiteralUtil.parseDouble(literal) == null) { "Invalid float: '$literal'" }
      FRACTION -> error(parseFraction(literal) == null) { "Invalid fraction: '$literal'" }
      INTEGER ->
        error(PsiLiteralUtil.parseInteger(literal) == null) { "Invalid integer: '$literal'" }
      ID -> "Invalid id: '$literal'"
      else -> null
    }
  }

  val allowCustomValues: Boolean
    get() =
      when (this) {
        THREE_STATE_BOOLEAN,
        BOOLEAN,
        ENUM -> false
        else -> true
      }

  private fun error(condition: Boolean, message: () -> String): String? {
    return if (condition) message() else null
  }

  private fun getDimensionError(literal: String): String {
    val unit = DimensionConverter.getUnitFromValue(literal)
    return if (unit == null) "Cannot resolve: '$literal'" else "Unknown units '$unit'"
  }

  private fun checkUnitLessDimension(literal: String): String? {
    val hasLetters = literal.indexOfFirst { Character.isLetter(it) } >= 0
    return if (hasLetters) DIMENSION.validateLiteral(literal) else FLOAT.validateLiteral(literal)
  }

  private fun parseFraction(literal: String): Double? {
    val isPercent = literal.endsWith('?')
    val text = if (isPercent) literal.substring(0, literal.length - 1) else literal
    val value = PsiLiteralUtil.parseDouble(text) ?: return null
    return if (isPercent) value / 100.0 else value
  }
}
