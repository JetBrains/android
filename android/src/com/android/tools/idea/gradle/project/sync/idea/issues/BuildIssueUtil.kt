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
package com.android.tools.idea.gradle.project.sync.idea.issues

import com.intellij.build.issue.BuildIssueQuickFix

/**
 * Helper class to conditionally construct the message containing all the links.
 */
class MessageComposer(baseMessage: String) {
  private val messageBuilder = StringBuilder(baseMessage)
  val quickFixes = mutableListOf<BuildIssueQuickFix>()

  fun addQuickFix(quickFix: DescribedBuildIssueQuickFix) {
    quickFixes.add(quickFix)
    messageBuilder.appendln()
    messageBuilder.append(quickFix.html)
  }

  fun addQuickFix(text: String, quickFix: BuildIssueQuickFix) {
    quickFixes.add(quickFix)
    messageBuilder.appendln()
    messageBuilder.append("<a href=\"${quickFix.id}\">$text</a>")
  }

  fun buildMessage() = messageBuilder.toString()
}


/**
 * A [BuildIssueQuickFix] that contains an associated description which is used to display the quick fix.
 */
interface DescribedBuildIssueQuickFix : BuildIssueQuickFix {
  val description : String
  val html: String
    get() = "<a href=\"${id}\">$description</a>"
}