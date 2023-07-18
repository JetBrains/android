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
package com.android.tools.idea.lint.common

import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.detector.api.Option
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.codeInsight.highlighting.TooltipLinkHandler
import com.intellij.openapi.editor.Editor

class LintInspectionDescriptionLinkHandler : TooltipLinkHandler() {
  override fun getDescription(refSuffix: String, editor: Editor): String? {
    val issue =
      BuiltinIssueRegistry().getIssue(refSuffix)
        ?: AndroidLintInspectionBase.findIssueByShortName(editor.project, refSuffix) ?: return null
    val html = issue.getExplanation(TextFormat.HTML)

    val description = buildString {
      append(html)
      append("<br><br>Issue id: ${issue.id}")

      val options = issue.getOptions()
      if (options.isNotEmpty()) {
        append("<br><br>${Option.describe(options, TextFormat.HTML, true)}")
      }

      val urls = issue.moreInfo
      if (urls.isNotEmpty()) {
        append("<br><br>More info:<br>")
        for (url in urls) {
          append("<a href=\"${url}\">${url}</a><br>")
        }
      }

      val vendor = issue.vendor ?: issue.registry?.vendor
      if (vendor != null) {
        append("<br>")
        vendor.describeInto(this, TextFormat.HTML, "")
      }
    }

    // IntelliJ seems to treat newlines in the HTML as needing to also be converted to <br> (whereas
    // Lint includes these for HTML readability but they shouldn't add additional lines since it has
    // already added <br> as well) so strip these out
    return description.replace("\n", "")
  }
}
