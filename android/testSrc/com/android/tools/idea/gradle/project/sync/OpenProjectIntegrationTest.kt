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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.GradleSnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.openGradleProject
import com.android.tools.idea.testing.reopenGradleProject
import com.android.tools.idea.util.runWhenSmartAndSynced
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer

class OpenProjectIntegrationTest : GradleSyncIntegrationTestCase(), GradleSnapshotComparisonTest {
  override fun useSingleVariantSyncInfrastructure(): Boolean = true

  fun testReopenProject() {
    openGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project") { }
    reopenGradleProject("project") { project ->
      assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.SKIPPED)
      var completed = false
      project.runWhenSmartAndSynced(testRootDisposable, callback = Consumer {
        completed = true
      })
      assertThat(completed).isTrue()
    }
  }
}