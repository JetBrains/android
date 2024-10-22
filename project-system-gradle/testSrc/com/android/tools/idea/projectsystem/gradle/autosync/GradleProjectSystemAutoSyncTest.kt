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
package com.android.tools.idea.projectsystem.gradle.autosync

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.sync.AutoSyncBehavior
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class GradleProjectSystemAutoSyncTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setup() {
    StudioFlags.SHOW_GRADLE_AUTO_SYNC_SETTING_UI.override(true)
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION)
  }

  @After
  fun cleanUp() {
    StudioFlags.SHOW_GRADLE_AUTO_SYNC_SETTING_UI.clearOverride()
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Default
  }

  @Test
  fun `Auto-sync disabled and request does not come from user directly`() {
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Manual
    val failure = assertFailsWith<RuntimeException> {
      projectRule.project.getSyncManager().requestSyncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED).get()
    }
    assertThat(failure.cause?.message).isEqualTo(
      "Some of the Android Studio features using Gradle require syncing so it has up-to-date information about your project. Sync the project to ensure the best Android Studio experience. You can snooze sync notifications for this session.")
  }

  @Test
  fun `Auto-sync disabled and request comes from user directly`() {
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Manual
    assertThat(projectRule.project.getSyncManager().requestSyncProject(ProjectSystemSyncManager.SyncReason.USER_REQUEST).get()).isEqualTo(
      SyncResult.SUCCESS)
  }
}
