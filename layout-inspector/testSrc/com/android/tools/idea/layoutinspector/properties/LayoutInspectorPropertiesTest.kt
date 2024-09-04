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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.UIUtil
import javax.swing.JPanel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val MODERN_PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class LayoutInspectorPropertiesTest {
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val inspectionRule = AppInspectionInspectorRule(projectRule)
  private val inspectorRule =
    LayoutInspectorRule(listOf(inspectionRule.createInspectorClientProvider()), projectRule) {
      it.name == MODERN_PROCESS.name
    }

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(inspectionRule).around(inspectorRule)!!

  @Test
  fun testInfoPanelVisibility() {
    val properties = LayoutInspectorProperties(projectRule.testRootDisposable)
    FakeUi(
      properties.component,
      createFakeWindow = true,
      parentDisposable = projectRule.testRootDisposable,
    )
    val infoText =
      UIUtil.findComponentsOfType(properties.component, JPanel::class.java).single {
        it.name == INFO_TEXT
      }
    val props =
      UIUtil.findComponentsOfType(properties.component, JPanel::class.java).single {
        it.name == PROPERTIES_COMPONENT_NAME
      }
    assertThat(infoText.isShowing).isTrue()
    assertThat(props.isShowing).isFalse()

    properties.setToolContext(inspectorRule.inspector)

    val window =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        compose(COMPOSE1, "Button", "button.kt") { compose(COMPOSE2, "Text", "text.kt") }
      }
    inspectorRule.inspectorModel.update(window, listOf(ROOT), 0)
    val compose1 = inspectorRule.inspectorModel.get(COMPOSE1)!!
    assertThat(infoText.isShowing).isTrue()
    assertThat(props.isShowing).isFalse()
    inspectorRule.inspectorModel.setSelection(compose1, SelectionOrigin.COMPONENT_TREE)
    assertThat(infoText.isShowing).isFalse()
    assertThat(props.isShowing).isTrue()
    inspectorRule.inspectorModel.setSelection(null, SelectionOrigin.INTERNAL)
    assertThat(infoText.isShowing).isTrue()
    assertThat(props.isShowing).isFalse()
  }
}
