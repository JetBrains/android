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
package com.android.tools.idea.gradle.project.sync.hyperlink.runsGradle

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.hyperlink.RemoveJcenterHyperlink
import com.android.tools.idea.gradle.project.sync.issues.processor.RemoveJcenterProcessor
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.intellij.openapi.command.WriteCommandAction
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

/**
 * Tests for [RemoveJcenterHyperlink]
 */
class RemoveJcenterHyperlinkTest: AndroidGradleTestCase() {
  @Test
  fun testExecuteNoRepository() {
    loadSimpleApplication()
    val mockProcessor = mock(RemoveJcenterProcessor::class.java)
    val quickfix = RemoveJcenterHyperlink(project, listOf())
    quickfix.applyFix(project, mockProcessor)
    verifyNoInteractions(mockProcessor)
  }

  @Test
  fun testExecuteProjectBuildGradle() {
    loadSimpleApplication()
    val projectBuildModel = ProjectBuildModel.get(project)
    val module = getModule("app")

    // Add jcenter to project build.gradle
    projectBuildModel.projectBuildModel!!.buildscript().repositories().addRepositoryByMethodName("jcenter")
    WriteCommandAction.runWriteCommandAction(project) {
      projectBuildModel.applyChanges()
    }

    val mockProcessor = mock(RemoveJcenterProcessor::class.java)
    val quickfix = RemoveJcenterHyperlink(project, listOf(module))
    quickfix.applyFix(project, mockProcessor)
    verify(mockProcessor).run()
  }

  @Test
  fun testExecuteProjectSettings() {
    loadSimpleApplication()
    val projectBuildModel = ProjectBuildModel.get(project)
    val module = getModule("app")

    // Add to settings.gradle
    projectBuildModel.projectSettingsModel!!.dependencyResolutionManagement().repositories().addRepositoryByMethodName("jcenter")
    WriteCommandAction.runWriteCommandAction(project) {
      projectBuildModel.applyChanges()
    }
    val mockProcessor = mock(RemoveJcenterProcessor::class.java)
    val quickfix = RemoveJcenterHyperlink(project, listOf(module))
    quickfix.applyFix(project, mockProcessor)
    verify(mockProcessor).run()
  }

  @Test
  fun testExecuteModuleBuildGradle() {
    loadSimpleApplication()
    val projectBuildModel = ProjectBuildModel.get(project)
    val module = getModule("app")

    // Add to module build.gradle
    projectBuildModel.getModuleBuildModel(module)!!.repositories().addRepositoryByMethodName("jcenter")
    WriteCommandAction.runWriteCommandAction(project) {
      projectBuildModel.applyChanges()
    }
    val mockProcessor = mock(RemoveJcenterProcessor::class.java)
    val quickfix = RemoveJcenterHyperlink(project, listOf(module))
    quickfix.applyFix(project, mockProcessor)
    verify(mockProcessor).run()
  }
}