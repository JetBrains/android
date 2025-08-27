/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.saveAndDump
import com.android.utils.FileUtils
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.jetbrains.annotations.SystemDependent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

@RunsInEdt
class GradleProjectImporterTest : SnapshotComparisonTest {
  @get:Rule
  val nameRule = TestName()
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  val integrationTestEnvironment = object : IntegrationTestEnvironment {
    override fun getBaseTestPath(): @SystemDependent String = FileUtils.toSystemIndependentPath(projectRule.fixture.tempDirPath)
  }

  override fun getName(): String = nameRule.methodName
  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"

  @Test
  fun testImportNoSync() {
    integrationTestEnvironment.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION, syncReady = false)
    val project = projectRule.project
    val request = GradleProjectImporter.Request(project)
    GradleProjectImporter.configureNewProject(project)
    GradleProjectImporter.getInstance().importProjectNoSync(request)
    refreshProjectFiles()
    val text = project.saveAndDump()
    assertIsEqualToSnapshot(text)
  }
}