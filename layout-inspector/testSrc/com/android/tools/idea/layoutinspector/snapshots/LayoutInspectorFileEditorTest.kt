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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.testutils.TestUtils
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.tree.EditorTreeSettings
import com.android.tools.idea.layoutinspector.ui.DeviceViewContentPanel
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.EditorDeviceViewSettings
import com.android.tools.idea.layoutinspector.util.ComponentUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class LayoutInspectorFileEditorTest {
  val projectRule = ProjectRule()
  val disposableRule = DisposableRule()

  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(disposableRule)!!

  @Test
  fun editorCreatesCorrectSettings() {
    val editor = LayoutInspectorFileEditor(
      projectRule.project,
      TestUtils.getWorkspaceRoot().resolve("$TEST_DATA_PATH/snapshot.li")
    )
    Disposer.register(disposableRule.disposable, editor)
    waitForCondition(5L, TimeUnit.SECONDS) {
      ComponentUtil.flatten(editor.component).firstIsInstanceOrNull<DeviceViewPanel>() != null
    }
    val settings = ComponentUtil.flatten(editor.component).firstIsInstance<DeviceViewContentPanel>().viewSettings
    assertThat(settings).isInstanceOf(EditorDeviceViewSettings::class.java)

    val inspector = DataManager.getDataProvider(ComponentUtil.flatten(editor.component).firstIsInstance<WorkBench<*>>())?.getData(
      LAYOUT_INSPECTOR_DATA_KEY.name) as LayoutInspector
    assertThat(inspector.treeSettings).isInstanceOf(EditorTreeSettings::class.java)
    assertThat(inspector.currentClient.capabilities).containsExactly(Capability.SUPPORTS_SYSTEM_NODES)
  }

  @Test
  fun editorCreatesCorrectSettingsForCompose() {
    val editor = LayoutInspectorFileEditor(
      projectRule.project,
      TestUtils.getWorkspaceRoot().resolve("$TEST_DATA_PATH/compose-snapshot.li")
    )
    Disposer.register(disposableRule.disposable, editor)
    waitForCondition(5L, TimeUnit.SECONDS) {
      ComponentUtil.flatten(editor.component).firstIsInstanceOrNull<DeviceViewPanel>() != null
    }
    val settings = ComponentUtil.flatten(editor.component).firstIsInstance<DeviceViewContentPanel>().viewSettings
    assertThat(settings).isInstanceOf(EditorDeviceViewSettings::class.java)

    val inspector = DataManager.getDataProvider(ComponentUtil.flatten(editor.component).firstIsInstance<WorkBench<*>>())?.getData(
      LAYOUT_INSPECTOR_DATA_KEY.name) as LayoutInspector
    assertThat(inspector.treeSettings).isInstanceOf(EditorTreeSettings::class.java)
    assertThat(inspector.currentClient.capabilities).containsExactly(
      Capability.SUPPORTS_SYSTEM_NODES, Capability.SUPPORTS_COMPOSE, Capability.SUPPORTS_SEMANTICS)
  }
}