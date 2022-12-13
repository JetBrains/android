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

abstract class VisualLintInspection(val type: VisualLintErrorType, val varName: String): GlobalInspectionTool() {
  override fun worksInBatchModeOnly() = false

  override fun getShortName() = type.shortName

  override fun getOptionsPane(): OptPane = pane(checkbox(varName, "Run in background"))

  override fun setOption(bindId: String, value: Any?) {
    super.setOption(bindId, value)
    if (bindId == varName) {
      VisualLintUsageTracker.getInstance().trackBackgroundRuleStatusChanged(type, value as Boolean)
    }
  }
}