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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetection
import com.android.tools.idea.layoutinspector.runningdevices.withAutoConnect
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.SelectDeviceAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.TargetSelectionActionFactory
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TargetSelectionActionFactoryTest {

  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val projectRule = ProjectRule()

  private lateinit var layoutInspector: LayoutInspector

  @Before
  fun setUp() {
    val scope = AndroidCoroutineScope(disposableRule.disposable)
    val deviceModel = mock<DeviceModel>()
    val processModel = mock<ProcessesModel>()
    val mockForegroundProcessDetection = mock<ForegroundProcessDetection>()
    val mockClientSettings = mock<InspectorClientSettings>()
    val mockLauncher = mock<InspectorClientLauncher>()
    whenever(mockLauncher.activeClient).thenAnswer { DisconnectedClient }
    val inspectorModel = InspectorModel(projectRule.project, scope)
    val mockTreeSettings = mock<TreeSettings>()
    layoutInspector =
      LayoutInspector(
        scope,
        processModel,
        deviceModel,
        mockForegroundProcessDetection,
        mockClientSettings,
        mockLauncher,
        inspectorModel,
        mock(),
        mockTreeSettings,
      )
  }

  @Test
  fun testDeviceSelectorIsCreated() =
    withAutoConnect(true) {
      val action = TargetSelectionActionFactory.getAction(layoutInspector)
      assertThat(action).isNotNull()
      assertThat(action!!.dropDownAction).isInstanceOf(SelectDeviceAction::class.java)
    }

  @Test
  fun testProcessSelectorIsCreated() =
    withAutoConnect(false) {
      val action = TargetSelectionActionFactory.getAction(layoutInspector)
      assertThat(action).isNotNull()
      assertThat(action!!.dropDownAction).isInstanceOf(SelectProcessAction::class.java)
    }
}
