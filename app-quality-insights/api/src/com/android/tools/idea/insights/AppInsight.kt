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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.analysis.Cause
import com.intellij.openapi.diagnostic.Logger

/**
 * Models an issue finding in the code.
 *
 * Do not keep references to instances of this class any longer that you would to for plain
 * [PsiElement]s.
 */
data class AppInsight(
  /** [line] where the Insight was found. */
  val line: Int,

  /** [Issue] found in this element. */
  val issue: AppInsightsIssue,

  /** The stack frame referencing this [element]. */
  val stackFrame: Frame,

  /** What triggered this error. */
  val cause: Cause,

  /**
   * Lambda used to make this insight the active/selected one.
   *
   * The [CrashlyticsState] uses the concept of a [Selection] to represent the list of issues, and
   * to mark one of them as the currently active/selected one. This action modifies the state this
   * Insight belongs to mark it as selected.
   */
  private val markAsSelectedCallback: (AppInsightsIssue) -> Unit,
) {
  fun markAsSelected() {
    Logger.getInstance(AppInsight::class.java).info("Mark issue as selected $issue")
    markAsSelectedCallback(issue)
  }
}
