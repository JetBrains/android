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
package com.android.tools.idea.gradle.project.sync.errors.integration

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.errors.AarDependencyCompatibilityIssue
import com.android.tools.idea.gradle.project.sync.errors.AarDependencyCompatibilityIssueChecker
import com.android.tools.idea.gradle.project.sync.errors.UpdateCompileSdkQuickFix
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.resolve
import com.android.tools.idea.testing.withCompileSdk
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptPsiFile
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

class AarDependencyCompatibilityIssueCheckerIntegrationTest: AndroidGradleTestCase() {
  @Test
  fun testAarDependencyCompatibilityIssue() {

    loadProject(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE, null,
                AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST.resolve().withCompileSdk("33").resolve(),
                null);
    val appModule = project.findAppModule()
    val libModule = project.findModule("mylibrary")
    val projectModel = ProjectBuildModel.get(project)
    val appBuildModel = projectModel.getModuleBuildModel(appModule)
    appBuildModel!!.dependencies().addModule("implementation", ":mylibrary")
    libModule.getBuildScriptPsiFile()?.virtualFile?.toIoFile()
      ?.appendText("\nandroid.defaultConfig.aarMetadata.minCompileSdk 34")
    ApplicationManager.getApplication().invokeAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        projectModel.applyChanges()
      }
    }
    val generatedExceptions = mutableListOf<Exception>()
    val taskNotificationListener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        generatedExceptions.add(e)
      }
    }
    AndroidGradleTaskManager().executeTasks(
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
      listOf(":app:assembleDebug"),
      project.basePath.orEmpty(),
      null,
      null,
      taskNotificationListener
    )
    Truth.assertThat(generatedExceptions).hasSize(1)
    Truth.assertThat(generatedExceptions[0]).isInstanceOf(BuildIssueException::class.java)
    val buildIssue = (generatedExceptions[0] as BuildIssueException).buildIssue
    Truth.assertThat(buildIssue).isInstanceOf(AarDependencyCompatibilityIssue::class.java)
    Truth.assertThat(buildIssue.quickFixes[0]).isInstanceOf(UpdateCompileSdkQuickFix::class.java)
    val quickFix = (buildIssue.quickFixes[0] as UpdateCompileSdkQuickFix)
    Truth.assertThat(quickFix.modulesWithSuggestedMinCompileSdk).isEqualTo(mapOf(":app" to 34))
  }
}
