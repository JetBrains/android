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
package com.android.tools.idea.common.error

import com.android.tools.idea.common.model.NlComponent
import com.intellij.lang.annotation.HighlightSeverity

internal class TooManyIssuesIssue(private val extraIssuesCount: Int): Issue() {
  override fun getSource(): NlComponent? = null
  override fun getSummary(): String =
    "Too many issues found. $extraIssuesCount not shown."

  override fun getDescription(): String = """
    Too many issues were found in this preview, not all of them will be shown in this panel.
    $extraIssuesCount were found and not displayed.
  """.trimIndent()
  override fun getCategory(): String = ""
  override fun getSeverity(): HighlightSeverity = HighlightSeverity.WEAK_WARNING
}