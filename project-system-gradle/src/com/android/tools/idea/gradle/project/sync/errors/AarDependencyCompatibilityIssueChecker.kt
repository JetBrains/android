/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.issues.processor.UpdateCompileSdkProcessor
import com.android.tools.idea.gradle.project.sync.quickFixes.moduleBuildFiles
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.concurrent.CompletableFuture

private const val MODULE_COMPILED_AGAINST_PATTERN = """(?<modulePath>\S+) is currently compiled against \S+."""
private const val COMPILE_SDK_UPDATE_RECOMMENDED_ACTION_PATTERN = """Recommended action: Update this project to use a newer compileSdk[\t\s\r\n]*of at least (?<minCompileSdk>\d+), for example \d+."""
private const val DEPENDENCY_PATTERN = """Dependency '.*' requires libraries and applications that[\t\s\r\n]*depend on it to compile against version \d+ or later of the[\t\s\r\n]*Android APIs."""
private val COMPILE_SDK_ISSUE_REGEX = """$DEPENDENCY_PATTERN[\t\s\r\n]*$MODULE_COMPILED_AGAINST_PATTERN[\t\s\r\n]*$COMPILE_SDK_UPDATE_RECOMMENDED_ACTION_PATTERN""".toRegex()

class AarDependencyCompatibilityIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    // Confirm rootCause is one of the expected causes.
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first ?: return null
    if (rootCause !is RuntimeException) {
      return null
    }
    val rootMessage = rootCause.message ?: return null
    val issueComposer = BuildIssueComposer(rootMessage, issueTitle = "Aar Dependency compatibility issues")
    val modulesWithSuggestedMinCompileSdk = mutableMapOf<String, Int>()
    var matchResult = COMPILE_SDK_ISSUE_REGEX.find(rootMessage)
    while (matchResult != null) {
      val modulePath = matchResult.groups["modulePath"]?.value
      if (modulePath != null) {
        val suggestedMinCompileSdk = matchResult.groups["minCompileSdk"]?.value?.toIntOrNull()
        if (suggestedMinCompileSdk != null) {
          modulesWithSuggestedMinCompileSdk.merge(modulePath, suggestedMinCompileSdk, Math::max)
        }
      }
      matchResult = matchResult.next()
    }
    if (modulesWithSuggestedMinCompileSdk.isEmpty()) {
      return null
    }
    issueComposer.addQuickFix(UpdateCompileSdkQuickFix(modulesWithSuggestedMinCompileSdk))
    return AarDependencyCompatibilityIssue(issueComposer.composeBuildIssue())
  }
}

class AarDependencyCompatibilityIssue(private val buildIssue: BuildIssue): BuildIssue {
  override val title = buildIssue.title
  override val description = buildIssue.description
  override val quickFixes = buildIssue.quickFixes
  override fun getNavigatable(project: Project) = buildIssue.getNavigatable(project)
}

class UpdateCompileSdkQuickFix(val modulesWithSuggestedMinCompileSdk: Map<String, Int>) : DescribedBuildIssueQuickFix {
  override val id = "update.modules.minCompileSdk"
  override val description = "Update minCompileSdk in modules with dependencies that require a higher minCompileSdk."

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    try {
      if (!project.isDisposed) {
        val buildFilesWithSuggestedMinCompileSdk = buildMap {
          for ((modulePath, minCompileSdk) in modulesWithSuggestedMinCompileSdk) {
            project.moduleBuildFiles(modulePath).forEach {
              put(it, minCompileSdk)
            }
          }
        }
        if (buildFilesWithSuggestedMinCompileSdk.isEmpty()) {
          // There is nothing to change, show an error message
          Messages.showErrorDialog(project, "Could not determine build files to apply fix", "Update minCompileSdk")
        }
        else {
          val processor = UpdateCompileSdkProcessor(project, buildFilesWithSuggestedMinCompileSdk)
          processor.setPreviewUsages(true)
          processor.run()
        }
      }
      future.complete(null)
    }
    catch (e: Exception) {
      future.completeExceptionally(e)
    }
    return future
  }
}