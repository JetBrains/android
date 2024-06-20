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

import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Helper class to conditionally construct the buildIssue containing all the information about a sync exception handling.
 */
class BuildIssueComposer(baseMessage: String, val issueTitle: String = "Gradle Sync issues.") {
  private val descriptionBuilder = StringBuilder(baseMessage)
  val issueQuickFixes = mutableListOf<BuildIssueQuickFix>()

  fun addDescription(message: String) {
    descriptionBuilder.appendLine()
    descriptionBuilder.appendLine(message)
  }

  fun addQuickFix(quickFix: DescribedBuildIssueQuickFix) {
    issueQuickFixes.add(quickFix)
    descriptionBuilder.appendLine()
    descriptionBuilder.append(quickFix.html)
  }

  fun addQuickFix(text: String, quickFix: BuildIssueQuickFix) {
    issueQuickFixes.add(quickFix)
    descriptionBuilder.appendLine()
    descriptionBuilder.append("<a href=\"${quickFix.id}\">$text</a>")
  }

  fun addQuickFix(prefix: String, text: String, suffix: String, quickFix: BuildIssueQuickFix) {
    issueQuickFixes.add(quickFix)
    descriptionBuilder.appendLine()
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

  fun composeErrorMessageAwareBuildIssue(buildErrorMessage: BuildErrorMessage) = object : ErrorMessageAwareBuildIssue {
    override val title: String = issueTitle
    override val description = descriptionBuilder.toString()
    override val quickFixes = issueQuickFixes
    override val buildErrorMessage = buildErrorMessage
    override fun getNavigatable(project: Project) = null
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

abstract class OpenLinkDescribedQuickFix : DescribedBuildIssueQuickFix {
  abstract val link: String
  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      BrowserUtil.browse(link)
      future.complete(null)
    }
    return future
  }
}

interface ErrorMessageAwareBuildIssue : BuildIssue {
  val buildErrorMessage: BuildErrorMessage
}
