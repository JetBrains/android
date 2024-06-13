/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.processor.runsGradle

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.issues.processor.RemoveJcenterProcessor
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.junit.Test

/**
 * Test for [RemoveJcenterProcessor]
 */
class RemoveJcenterProcessorTest : AndroidGradleTestCase() {
  @Test
  fun testRemovedFromApp() {
    loadSimpleApplication()
    var projectBuildModel = ProjectBuildModel.get(project)
    val module = getModule("app")

    // Add jcenter to project build.gradle
    projectBuildModel.projectBuildModel!!.buildscript().repositories().addRepositoryByMethodName("jcenter")

    // Add to module build.gradle
    projectBuildModel.getModuleBuildModel(module)!!.repositories().addRepositoryByMethodName("jcenter")

    // Add to settings.gradle
    projectBuildModel.projectSettingsModel!!.dependencyResolutionManagement().repositories().addRepositoryByMethodName("jcenter")

    WriteCommandAction.runWriteCommandAction(project) {
      projectBuildModel.applyChanges()
    }

    // Confirm it is there
    projectBuildModel = ProjectBuildModel.get(project)
    assertThat(projectBuildModel.projectBuildModel!!.buildscript().repositories().containsMethodCall("jcenter"))
    assertThat(projectBuildModel.getModuleBuildModel(module)!!.repositories().containsMethodCall("jcenter"))
    assertThat(projectBuildModel.projectSettingsModel!!.dependencyResolutionManagement().repositories().containsMethodCall("jcenter"))

    val processor = RemoveJcenterProcessor(project, listOf(module))

    // Verify expected usages
    val usages = processor.findUsages()
    assertThat(usages).hasLength(3)

    // Run processor
    var synced = false
    GradleSyncState.subscribe(project, object : GradleSyncListener {
      override fun syncSucceeded(project: Project) {
        synced = true
      }
    })
    WriteCommandAction.runWriteCommandAction(project) {
      processor.performRefactoring(usages)
    }

    // Confirm sync was successful and repository was removed
    assertTrue(synced)
    projectBuildModel = ProjectBuildModel.get(project)
    assertThat(!projectBuildModel.projectBuildModel!!.buildscript().repositories().containsMethodCall("jcenter"))
    assertThat(!projectBuildModel.getModuleBuildModel(module)!!.repositories().containsMethodCall("jcenter"))
    assertThat(!projectBuildModel.projectSettingsModel!!.dependencyResolutionManagement().repositories().containsMethodCall("jcenter"))
  }
}

