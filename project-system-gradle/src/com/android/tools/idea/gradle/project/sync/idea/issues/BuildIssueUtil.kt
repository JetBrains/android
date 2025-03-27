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
  val descriptionComposer = BuildIssueDescriptionComposer(baseMessage)

  val issueQuickFixes: List<BuildIssueQuickFix> get() = descriptionComposer.quickFixes

  fun addDescriptionOnNewLine(message: String): BuildIssueComposer {
    descriptionComposer.newLine()
    descriptionComposer.addDescription(message)
    return this
  }

  fun startNewParagraph(): BuildIssueComposer {
    descriptionComposer.newLine()
    return this
  }

  fun addQuickFix(quickFix: DescribedBuildIssueQuickFix): BuildIssueComposer {
    descriptionComposer.newLine()
    descriptionComposer.addQuickFix(quickFix)
    return this
  }

  fun addQuickFix(text: String, quickFix: BuildIssueQuickFix): BuildIssueComposer {
    descriptionComposer.newLine()
    descriptionComposer.addQuickFix(text, quickFix)
    return this
  }

  fun addQuickFix(prefix: String, text: String, suffix: String, quickFix: BuildIssueQuickFix): BuildIssueComposer {
    descriptionComposer.newLine()
    descriptionComposer.addQuickFix(prefix, text, suffix, quickFix)
    return this
  }

  fun addDescriptionOnNewLine(additionalDescription: BuildIssueDescriptionComposer): BuildIssueComposer {
    descriptionComposer.newLine()
    descriptionComposer.addDescription(additionalDescription)
    return this
  }

  fun composeBuildIssue(): BuildIssue {
    return object : BuildIssue {
      override val title: String = issueTitle
      override val description = descriptionComposer.description
      override val quickFixes = descriptionComposer.quickFixes
      override fun getNavigatable(project: Project) = null
    }
  }

  fun composeErrorMessageAwareBuildIssue(buildErrorMessage: BuildErrorMessage) = object : ErrorMessageAwareBuildIssue {
    override val title: String = issueTitle
    override val description = descriptionComposer.description
    override val quickFixes = descriptionComposer.quickFixes
    override val buildErrorMessage = buildErrorMessage
    override fun getNavigatable(project: Project) = null
  }
}

class BuildIssueDescriptionComposer(baseMessage: String = "") {
  private val descriptionBuilder = StringBuilder(baseMessage.trimEnd())
  private val issueQuickFixes = mutableListOf<BuildIssueQuickFix>()

  val description: String
    get() = descriptionBuilder.toString()
  val quickFixes: List<BuildIssueQuickFix>
    get() = issueQuickFixes


  fun addDescription(additionalDescription: BuildIssueDescriptionComposer) {
    descriptionBuilder.append(additionalDescription.description)
    issueQuickFixes.addAll(additionalDescription.issueQuickFixes)
  }

  fun newLine() {
    descriptionBuilder.appendLine()
  }

  fun addDescription(message: String) {
    descriptionBuilder.append(message)
  }

  fun addQuickFix(quickFix: DescribedBuildIssueQuickFix) {
    addQuickFixInternal(quickFix.html, quickFix)
  }

  fun addQuickFix(text: String, quickFix: BuildIssueQuickFix) {
    addQuickFix(prefix = "", text = text, suffix = "", quickFix = quickFix)
  }

  fun addQuickFix(prefix: String, text: String, suffix: String, quickFix: BuildIssueQuickFix) {
    addQuickFixInternal("$prefix<a href=\"${quickFix.id}\">$text</a>$suffix", quickFix)
  }

  private fun addQuickFixInternal(html: String, quickFix: BuildIssueQuickFix) {
    issueQuickFixes.add(quickFix)
    descriptionBuilder.append(html)
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
