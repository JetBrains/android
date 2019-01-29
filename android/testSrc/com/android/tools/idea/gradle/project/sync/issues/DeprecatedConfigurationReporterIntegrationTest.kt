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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.errors.SdkBuildToolsTooLowErrorHandler
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH1_DOT5
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class DeprecatedConfigurationReporterIntegrationTest : AndroidGradleTestCase() {
  private var reporter : BuildToolsTooLowReporter? = null
  private var handler : SdkBuildToolsTooLowErrorHandler? = null

  @Before
  override fun setUp() {
    super.setUp()
    handler = mock(SdkBuildToolsTooLowErrorHandler::class.java)
    reporter = BuildToolsTooLowReporter(handler!!)
  }

  @Test
  fun testModuleLink() {
    loadProject(PROJECT_WITH1_DOT5)
    val appModule = myModules.appModule
    val appFile = GradleUtil.getGradleBuildFile(appModule)!!

    val project = project
    val buildModel = ProjectBuildModel.get(project)

    val issue = mock(SyncIssue::class.java)

    val syncIssues = ImmutableList.of<SyncIssue>(issue)
    val link = reporter!!.createModuleLink(getProject(), appModule, buildModel, syncIssues, appFile)
    assertThat(link.lineNumber).isEqualTo(19)
    assertThat(link.filePath).isEqualTo(appFile.path)
  }
}