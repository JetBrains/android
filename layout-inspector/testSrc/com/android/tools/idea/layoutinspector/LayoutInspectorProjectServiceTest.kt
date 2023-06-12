/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class LayoutInspectorProjectServiceTest {

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @Test
  fun testCreateProcessesModel() {
    val modernProcess = MODERN_DEVICE.createProcess()
    val legacyProcess = LEGACY_DEVICE.createProcess()
    val olderLegacyProcess = OLDER_LEGACY_DEVICE.createProcess()

    val processDiscovery = TestProcessDiscovery()
    val model = createProcessesModel(
      projectRule.project, disposableRule.disposable, processDiscovery, MoreExecutors.directExecutor()
    )

    // Verify that devices older than M will be included in the processes model:
    processDiscovery.fireConnected(olderLegacyProcess)
    assertThat(model.processes).hasSize(1)
    // An M device as well:
    processDiscovery.fireConnected(legacyProcess)
    assertThat(model.processes).hasSize(2)
    // And newer devices as well:
    processDiscovery.fireConnected(modernProcess)
    assertThat(model.processes).hasSize(3)
  }
}