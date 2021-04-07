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

import com.android.tools.idea.gradle.project.sync.idea.svs.AndroidSyncException
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class SdkPlatformNotFoundException(message: String) : AndroidSyncException(message)

class SdkPlatformNotFoundIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    if (issueData.error !is SdkPlatformNotFoundException) return null

    return object : BuildIssue {
      override val title: String = "SDK Setup Issues"
      override val description: String = issueData.error.message!!
      override val quickFixes: List<BuildIssueQuickFix> = listOf()
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}
