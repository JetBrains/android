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

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGradleBuildFile
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.findAppModule
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock

class BuildToolsTooLowReporterIntegrationTest : AndroidGradleTestCase() {
  private var reporter : BuildToolsTooLowReporter? = null

  override fun setUp() {
    super.setUp()
    reporter = BuildToolsTooLowReporter()
  }

  @Test
  fun testModuleLink() {
    loadSimpleApplication()
    val appModule = project.findAppModule()
    val appFile = getGradleBuildFile(appModule)!!

    val issue = mock(IdeSyncIssue::class.java)

    val syncIssues = ImmutableList.of(issue)
    val link = reporter!!.createModuleLink(project, appModule, syncIssues, appFile)
    assertThat(link.lineNumber).isEqualTo(-1)
    assertThat(link.filePath).isEqualTo(appFile.path)
  }
}