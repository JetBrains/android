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
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.findFromBuildFiles
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.issues.processor.AddRepoProcessor
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
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
    val future = CompletableFuture<Any>()
    if (!project.isInitialized) {
      invokeLater {
        Messages.showErrorDialog(project, "Failed to add Google Maven repository.", "Quick Fix")
        future.complete(null)
      }
    }
    else {
      ReadAction.nonBlocking<AndroidPluginInfo?> { findFromBuildFiles(project) }
        .inSmartMode(project)
        .coalesceBy(project, this)
        .finishOnUiThread(ModalityState.defaultModalityState()) { pluginInfo ->
          addGoogleMavenRepoPreview(pluginInfo, project)
          future.complete(null)
        }.submit(AppExecutorUtil.getAppExecutorService())
    }

    return future
  }

  private fun addGoogleMavenRepoPreview(pluginInfo: AndroidPluginInfo?, project: Project) {
    val projectBuildModel: ProjectBuildModel = ProjectBuildModel.getOrLog(project) ?: return
    val buildFile: VirtualFile = pluginInfo?.pluginBuildFile
                                 ?: getGradleBuildFile(getBaseDirPath(project))
                                 ?: return

    val gradleBuildModel: GradleBuildModel = projectBuildModel.getModuleBuildModel(buildFile)
    // Only add the google Maven repository if it doesn't already exist.
    if (!gradleBuildModel.buildscript().repositories().hasGoogleMavenRepository()) {
      val processor = AddRepoProcessor(project, listOf(buildFile), AddRepoProcessor.Repository.GOOGLE, true)
      processor.setPreviewUsages(true)
      processor.run()
    }
  }
}