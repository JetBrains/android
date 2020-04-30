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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.getErrorLocation
import com.android.tools.idea.gradle.project.sync.idea.issues.MessageComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.io.File

class GenericIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    val description = getBuildIssueDescription(message, issueData.filePosition) ?: return null
    return object : BuildIssue {
      override val title = "Gradle Sync issues."
      override val description = description.buildMessage()
      override val quickFixes = description.quickFixes
      override fun getNavigatable(project: Project) = null
    }
  }

  private fun getBuildIssueDescription(message: String, fileLocation: FilePosition?): MessageComposer? {
    val description = MessageComposer(message)
    if (message.isNotEmpty()) {
      val lines = message.lines()
      val errLocation = getErrorLocation(lines[lines.size - 1])
      if (errLocation != null) {
        description.addQuickFix("Open file",
                                OpenFileAtLocationQuickFix(FilePosition(File(errLocation.first), errLocation.second - 1, -1)))
        return description
      }
    }
    if (fileLocation == null) return null
    description.addQuickFix("Open file", OpenFileAtLocationQuickFix(fileLocation))
    return description
  }
}