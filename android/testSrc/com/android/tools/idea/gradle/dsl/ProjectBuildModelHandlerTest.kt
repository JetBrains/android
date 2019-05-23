/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModelHandler
import com.android.tools.idea.gradle.project.sync.GradleFiles
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.android.AndroidTestCase
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class ProjectBuildModelHandlerTest : AndroidTestCase() {
  @Mock lateinit var projectBuildModel: ProjectBuildModel

  @Before
  override fun setUp() {
    super.setUp()
    projectBuildModel = mock(ProjectBuildModel::class.java)
  }

  private fun setupGradleSyncState(timeStamp: Long) {
    val gradleSyncState = mock(GradleSyncState::class.java)
    `when`(gradleSyncState.lastSyncFinishedTimeStamp).thenReturn(timeStamp)
    replaceProjectService(GradleSyncState::class.java, gradleSyncState)
  }

  private fun setupGradleFiles(modified: Boolean) {
    val gradleFiles = mock(GradleFiles::class.java)
    `when`(gradleFiles.areGradleFilesModified()).thenReturn(modified)
    replaceProjectService(GradleFiles::class.java, gradleFiles)
  }

  @Test
  fun testReuseExistingModel() {
    setupGradleSyncState(3L)
    setupGradleFiles(false)
    val handler = ProjectBuildModelHandler(project, projectBuildModel, 3L)
    val buildModel = projectBuildModel
    handler.read {
      assertTrue(buildModel === this)
    }
    handler.modify {
      assertTrue(buildModel === this)
    }
  }

  @Test
  fun testRecreateModelOnFilesModified() {
    setupGradleSyncState(4L)
    setupGradleFiles(true)
    val handler = ProjectBuildModelHandler(project, projectBuildModel, 4L)
    var buildModel = projectBuildModel
    handler.modify {
      assertFalse(buildModel === this)
      buildModel = this
    }
    handler.read {
      assertFalse(buildModel === this)
    }
  }

  @Test
  fun testRecreateModelOnNewSync() {
    setupGradleSyncState(5L)
    setupGradleFiles(false)
    val handler = ProjectBuildModelHandler(project, projectBuildModel, 4L)
    var buildModel = projectBuildModel
    handler.read {
      assertFalse(buildModel === this)
      buildModel = this
    }
    setupGradleSyncState(6L)
    handler.modify {
      assertFalse(buildModel === this)
    }
  }

  @Test
  fun testForWriteAppliesModel() {
    setupGradleSyncState(6L)
    setupGradleFiles(false)
    val handler = ProjectBuildModelHandler(project, projectBuildModel, 6L)
    `when`(projectBuildModel.applyChanges()).then {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
    }
    handler.modify { }
    verify(projectBuildModel).applyChanges()
  }
}