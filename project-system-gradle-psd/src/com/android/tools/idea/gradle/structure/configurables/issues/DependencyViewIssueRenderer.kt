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
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.getHyperlinkDestination
import com.android.tools.idea.gradle.structure.model.parents
import com.intellij.openapi.util.text.StringUtil

class DependencyViewIssueRenderer(
  private val context: PsContext,
  private val renderDescription: Boolean
) : IssueRenderer {

  override fun renderIssue(buffer: StringBuilder, issue: PsIssue, scope: PsPath?) {
    (issue.path.parents + issue.path).asReversed().takeWhile { it != scope }.asReversed().forEach { parentPath ->
      val parentPathHref = parentPath.getHyperlinkDestination(context)
      if (parentPathHref != null) {
        val parentPathText = parentPath.toString().makeTextWrappable()
        buffer.append("<a href=\"$parentPathHref\">$parentPathText</a>: ")
      }
    }
    buffer.append(issue.text)
    if (issue.quickFixes.isNotEmpty()) {
      buffer.append("<br/>")
    }
    issue.quickFixes.forEach { quickFix ->
      buffer.append(" <a href='${quickFix.getHyperlinkDestination()}'>[${quickFix.text}]</a>")
    }
    if (renderDescription) {
      val description = issue.description
      if (StringUtil.isNotEmpty(description)) {
        buffer.append("<br/><br/>").append(description)
      }
    }
  }
}