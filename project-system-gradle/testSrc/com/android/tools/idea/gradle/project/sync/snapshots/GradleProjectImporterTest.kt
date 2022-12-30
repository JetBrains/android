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
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.saveAndDump

@org.junit.Ignore("b/264602704")
class GradleProjectImporterTest : AndroidGradleTestCase(), SnapshotComparisonTest {
  fun testImportNoSync() {
    prepareProjectForImport(TestProjectToSnapshotPaths.SIMPLE_APPLICATION)
    val project = super.getProject()
    val request = GradleProjectImporter.Request(project)
    GradleProjectImporter.configureNewProject(project)
    GradleProjectImporter.getInstance().importProjectNoSync(request)
    refreshProjectFiles()
    val text = project.saveAndDump()
    assertIsEqualToSnapshot(text)
  }

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"
}