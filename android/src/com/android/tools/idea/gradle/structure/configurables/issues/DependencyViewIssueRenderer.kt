/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.issues

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.intellij.openapi.util.text.StringUtil

class DependencyViewIssueRenderer(
  private val context: PsContext,
  private val renderPath: Boolean,
  private val renderDescription: Boolean
) : IssueRenderer {

  override fun renderIssue(buffer: StringBuilder, issue: PsIssue) {
    if (renderPath) {
      buffer.append(issue.path.getHtml(context))
      buffer.append(": ")
    }
    buffer.append(issue.text)
    val quickFixPath = issue.quickFix
    if (quickFixPath != null) {
      buffer.append(" ").append(quickFixPath.getHtml(context))
    }
    if (renderDescription) {
      val description = issue.description
      if (StringUtil.isNotEmpty(description)) {
        buffer.append("<br/><br/>").append(description)
      }
    }
  }
}