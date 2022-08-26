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
package com.android.tools.idea.gradle.project.sync.idea.issues

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueUsageReporter.Companion.getInstance
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * Helper class to conditionally construct the buildIssue containing all the information about a sync exception handling.
 */
class BuildIssueComposer(baseMessage: String, val issueTitle: String = "Gradle Sync issues.") {
  private val descriptionBuilder = StringBuilder(baseMessage)
  val issueQuickFixes = mutableListOf<BuildIssueQuickFix>()

  fun addDescription(message: String) {
    descriptionBuilder.appendln()
    descriptionBuilder.appendln(message)
  }

  fun addQuickFix(quickFix: DescribedBuildIssueQuickFix) {
    issueQuickFixes.add(quickFix)
    descriptionBuilder.appendln()
    descriptionBuilder.append(quickFix.html)
  }

  fun addQuickFix(text: String, quickFix: BuildIssueQuickFix) {
    issueQuickFixes.add(quickFix)
    descriptionBuilder.appendln()
    descriptionBuilder.append("<a href=\"${quickFix.id}\">$text</a>")
  }

  fun addQuickFix(prefix: String, text: String, suffix: String, quickFix: BuildIssueQuickFix) {
    issueQuickFixes.add(quickFix)
    descriptionBuilder.appendln()
    descriptionBuilder.append("$prefix<a href=\"${quickFix.id}\">$text</a>$suffix")
  }

  fun composeBuildIssue(): BuildIssue {
    return object : BuildIssue {
      override val title: String = issueTitle
      override val description = descriptionBuilder.toString()
      override val quickFixes = issueQuickFixes
      override fun getNavigatable(project: Project) = null
    }
  }
}

//TODO(karimai): This is a workaround until I refactor the services related to reporting sync Metrics to use Gradle project paths.
fun updateUsageTracker(projectPath: String, gradleSyncFailure: GradleSyncFailure) {
  for (project in ProjectManager.getInstance().openProjects) {
    if (project.basePath == projectPath) {
      getInstance(project).collect(gradleSyncFailure)
      break
    }
  }
}

/**
 * Find the Idea project instance associated with a given Gradle project's external path.
 */
//TODO(karimai): Move when SyncIssueUsageReporter is re-worked.
fun fetchIdeaProjectForGradleProject(projectPath: String): Project? {
  return runReadAction {
    val projectVirtualFile = VfsUtil.findFileByIoFile(File(projectPath), false) ?: return@runReadAction null
    ProjectManager.getInstance().openProjects.firstOrNull {
      ProjectFileIndex.getInstance(it).isInContent(projectVirtualFile)
    }
  }
}

/**
 * A [BuildIssueQuickFix] that contains an associated description which is used to display the quick fix.
 */
interface DescribedBuildIssueQuickFix : BuildIssueQuickFix {
  val description : String
  val html: String
    get() = "<a href=\"${id}\">$description</a>"
}