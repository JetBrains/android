/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.SdkConstants

enum class VisualLintErrorType(val shortName: String) {
  TEXT_FIELD_SIZE("TextFieldSize"),
  BUTTON_SIZE("ButtonSize"),
  BOUNDS("Bounds"),
  BOTTOM_NAV("BottomNav"),
  BOTTOM_APP_BAR("BottomAppBar"),
  OVERLAP("Overlap"),
  LONG_TEXT("LongText"),
  ATF("AccessibilityTestFramework"),
  LOCALE_TEXT("LocaleText");

  /**
   * The values for tools:ignore attribute. This is used by suppression.
   */
  val ignoredAttributeValue: String
    get() = ATTRIBUTE_PREFIX + shortName

  fun toSuppressActionDescription(): String {
    return """Add ${SdkConstants.TOOLS_NS_NAME_PREFIX}${SdkConstants.ATTR_IGNORE}="$ignoredAttributeValue""""
  }

  companion object {
    private const val ATTRIBUTE_PREFIX = "VisualLint"

    fun getTypeByIgnoredAttribute(value: String): VisualLintErrorType? {
      return values().firstOrNull { it.ignoredAttributeValue == value }
    }
  }
}
