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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_FOREGROUND
import com.android.SdkConstants.ATTR_GRAVITY
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import java.util.Locale

/**
 * Conversion of legacy layout inspector properties to recognizable attribute names and values.
 */
object PropertyMapper {
  private val gravityMapping by lazy(LazyThreadSafetyMode.NONE) { GravityIntMapping() }

  fun exclude(fieldName: String): Boolean {
    return fieldName.startsWith("layout_mMarginFlags")
  }

  fun mapPropertyName(fieldName: String): String {
    when (fieldName) {
      "mID" -> return ATTR_ID
      "mCurTextColor" -> return ATTR_TEXT_COLOR
      "layout_topMargin" -> return ATTR_LAYOUT_MARGIN_TOP
      "layout_bottomMargin" -> return ATTR_LAYOUT_MARGIN_BOTTOM
      "layout_leftMargin" -> return ATTR_LAYOUT_MARGIN_LEFT
      "layout_rightMargin" -> return ATTR_LAYOUT_MARGIN_RIGHT
      "layout_startMargin" -> return ATTR_LAYOUT_MARGIN_START
      "layout_endMargin" -> return ATTR_LAYOUT_MARGIN_END
    }
    if (fieldName.startsWith("m") && fieldName.length > 1) {
      return decapitalize(fieldName.substring(1))
    }
    if (fieldName.startsWith("bg_")) {
      return ATTR_BACKGROUND
    }
    if (fieldName.startsWith("fg_")) {
      return ATTR_FOREGROUND
    }
    if (fieldName.startsWith("get") && fieldName.endsWith("()") && fieldName.length > 5) {
      return decapitalize(fieldName.substring(3, fieldName.length - 2))
    }
    if (fieldName.endsWith("()") && fieldName.length > 2) {
      return decapitalize(fieldName.substring(0, fieldName.length - 2))
    }
    return fieldName
  }

  fun mapPropertyType(fieldName: String, attributeName: String, rawValue: String): Type {
    when (attributeName) {
      ATTR_TEXT_COLOR -> return Type.COLOR
      ATTR_BACKGROUND -> return Type.DRAWABLE
      ATTR_FOREGROUND -> return Type.DRAWABLE
    }
    if ((fieldName.startsWith("is") || fieldName.startsWith("has")) && fieldName.endsWith("()")) {
      return Type.BOOLEAN
    }
    if (rawValue.all { it.isDigit() || it == '.' || it == '-' }) {
      if (rawValue.indexOf('.') < 0) {
        return Type.INT32
      }
      return Type.FLOAT
    }
    if (rawValue.startsWith("0x") && rawValue.substring(2).all { it.isDigit() || (it in 'A'..'F') }) {
      return Type.INT32
    }
    return Type.STRING
  }

  fun mapPropertyValue(attributeName: String, type: Type, rawValue: String): String? {
    try {
      if (rawValue.isEmpty() || rawValue == "null") {
        return null
      }
      when (attributeName) {
        ATTR_ID -> return if (rawValue.startsWith("@")) rawValue else "@$rawValue"
        ATTR_LAYOUT_GRAVITY,
        ATTR_GRAVITY -> return gravityMapping.fromIntValue(toInt(rawValue) ?: 0).joinToString(separator="|")
      }
      when (type) {
        Type.DRAWABLE,
        Type.COLOR -> return "#%8x".format(rawValue.toInt())
        else -> return rawValue
      }
    }
    catch (ex: Exception) {
      return rawValue
    }
  }

  private fun toInt(value: String): Int? {
    if (value.startsWith("0x")) {
      return value.substring(2).toIntOrNull(16)
    }
    else {
      return value.toIntOrNull()
    }
  }

  /**
   * Same as String.decapitalize() but implemented here to avoid compiler errors/warnings.
   *
   * The problems:
   *  - String.decapitalize() is flagged by kotlin linter and recommends String.decapitalize(Locale)
   *  - String.decapitalize(Locale) is flagged by the kotlin compiler as ExperimentalStdlibApi
   */
  private fun decapitalize(str: String): String {
    val first = str.substring(0, 1).toLowerCase(Locale.getDefault())
    return "$first${str.substring(1)}"
  }
}
