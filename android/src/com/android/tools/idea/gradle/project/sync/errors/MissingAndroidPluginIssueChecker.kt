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

import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.findFromBuildFiles
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.issues.processor.AddRepoProcessor
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile
import com.android.tools.idea.npw.invokeLater
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class MissingAndroidPluginIssueChecker : GradleIssueChecker {
  private val PATTERN = "Could not find com.android.tools.build:gradle:"

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (!message.startsWith(PATTERN)) return null

    return BuildIssueComposer(message).apply {
      // Display the link to the quickFix, but it will only effectively write to the build file is the block doesn't exist already.
      addQuickFix("Add google Maven repository and sync project", AddGoogleMavenRepositoryQuickFix())
      addQuickFix("Open File", OpenPluginBuildFileQuickFix())
    }.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return failureCause.startsWith(PATTERN)
  }

}

class AddGoogleMavenRepositoryQuickFix : BuildIssueQuickFix {
  override val id = "add.google.maven.repo"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    invokeLater {
      if (project.isInitialized) {
        val pluginInfo = findFromBuildFiles(project)
        val buildFile =
          if (pluginInfo != null) pluginInfo.pluginBuildFile else getGradleBuildFile(getBaseDirPath(project)) ?: return@invokeLater
        // Only add the google Maven repository if it doesn't already exist.
        // TODO(karimai): Could there be a case when this condition is not always true ?
        val projectBuildModel = ProjectBuildModel.getOrLog(project) ?: return@invokeLater
        val gradleBuildModel = projectBuildModel.getModuleBuildModel(buildFile!!)
        if (!gradleBuildModel.buildscript().repositories().hasGoogleMavenRepository()) {
          val processor = AddRepoProcessor(project, listOf(buildFile), AddRepoProcessor.Repository.GOOGLE, true)
          processor.setPreviewUsages(true)
          processor.run()
        }
      }
      else Messages.showErrorDialog(project, "Failed to add Google Maven repository.", "Quick Fix")
    }
    return CompletableFuture.completedFuture<Any>(null)
  }
}