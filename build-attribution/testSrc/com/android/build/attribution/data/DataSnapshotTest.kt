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
package com.android.build.attribution.data

import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.analyzers.runTest
import com.android.build.attribution.getSuccessfulResult
import com.android.build.attribution.utils.BuildAnalysisResultsSnapshotGenerator
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class DataSnapshotTest: SnapshotComparisonTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val testName: TestName = TestName()

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/build-attribution/testData/snapshots/"
  override fun getName(): String = testName.methodName

  @Before
  fun setUp() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.clearOverride()
  }

  @Test
  fun testTaskAndPluginData() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BUILD_ANALYZER_CHECK_ANALYZERS)

    preparedProject.runTest {
      invokeTasks("assembleDebug")
      val results = project.getService(BuildAnalyzerStorageManager::class.java).getSuccessfulResult()
      assertIsEqualToSnapshot(BuildAnalysisResultsSnapshotGenerator().dump(results))
    }
  }
}