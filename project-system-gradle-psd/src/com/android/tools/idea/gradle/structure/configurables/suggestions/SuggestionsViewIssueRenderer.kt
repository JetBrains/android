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
package com.android.tools.idea.gradle.structure.configurables.suggestions

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.issues.IssueRenderer
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.parents

class SuggestionsViewIssueRenderer(val context: PsContext) : IssueRenderer {

  override fun renderIssue(buffer: StringBuilder, issue: PsIssue, scope: PsPath?) {
    val issuePath = issue.path
    val issuePathHref = issuePath.getHyperlinkDestination(context)
    val issuePathText = issuePath.toString().makeTextWrappable()
    val issueText = issue.text.makeTextWrappable()

    buffer.append("<b>")
    issuePath.parents.asReversed().takeWhile { it != scope }.asReversed().let { parents ->
      parents.forEachIndexed { index, parentPath ->
        if (parentPath.canHide) return@forEachIndexed
        val parentPathHref = parentPath.getHyperlinkDestination(context)
                             ?: (if (index < parents.size - 1) parents[index + 1].getHyperlinkDestination(context) else null)
        if (parentPathHref != null) {
          val parentPathText = parentPath.toString().makeTextWrappable()
          buffer.append("<a href=\"$parentPathHref\">$parentPathText</a> » ")
        }
      }
      buffer.append(issuePathText)
    }
    buffer.append("</b><p>")
    buffer.append(issueText)
    if (issuePathHref != null) {
      buffer.append("<br/><a href='$issuePathHref'>View usage</a>")
    }
  }
}
