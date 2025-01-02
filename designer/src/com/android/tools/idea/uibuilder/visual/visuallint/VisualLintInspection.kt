/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptionController

abstract class VisualLintInspection(val type: VisualLintErrorType) : GlobalInspectionTool() {
  var runInBackground = true

  override fun worksInBatchModeOnly() = false

  override fun getShortName() = type.shortName

  override fun getOptionsPane(): OptPane =
    pane(checkbox(this::runInBackground.name, "Run in background"))

  override fun getOptionController(): OptionController {
    return super.getOptionController().onValueSet(this::runInBackground.name) {
      VisualLintUsageTracker.getInstance().trackBackgroundRuleStatusChanged(type, it as Boolean)
    }
  }
}

class AtfAnalyzerInspection : VisualLintInspection(VisualLintErrorType.ATF)

class BottomAppBarAnalyzerInspection : VisualLintInspection(VisualLintErrorType.BOTTOM_APP_BAR)

class BottomNavAnalyzerInspection : VisualLintInspection(VisualLintErrorType.BOTTOM_NAV)

class BoundsAnalyzerInspection : VisualLintInspection(VisualLintErrorType.BOUNDS)

class ButtonSizeAnalyzerInspection : VisualLintInspection(VisualLintErrorType.BUTTON_SIZE)

class LocaleAnalyzerInspection : VisualLintInspection(VisualLintErrorType.LOCALE_TEXT)

class LongTextAnalyzerInspection : VisualLintInspection(VisualLintErrorType.LONG_TEXT)

class OverlapAnalyzerInspection : VisualLintInspection(VisualLintErrorType.OVERLAP)

class TextFieldSizeAnalyzerInspection : VisualLintInspection(VisualLintErrorType.TEXT_FIELD_SIZE)

class WearMarginAnalyzerInspection : VisualLintInspection(VisualLintErrorType.WEAR_MARGIN)
