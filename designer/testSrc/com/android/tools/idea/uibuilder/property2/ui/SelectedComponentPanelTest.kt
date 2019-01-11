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
package com.android.tools.idea.uibuilder.property2.ui

import com.android.SdkConstants
import com.android.tools.idea.common.property2.impl.model.GenericInspectorLineModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.model.SelectedComponentModel
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel

@RunsInEdt
class SelectedComponentPanelTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Test
  fun testUpdates() {
    val util = SupportTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    val property = util.makeIdProperty()
    val model = SelectedComponentModel(property, util.components, "What?")
    val panel = SelectedComponentPanel(model)
    val label = panel.getComponent(0) as JLabel
    val line = GenericInspectorLineModel()
    panel.lineModel = line

    assertThat(label.text).isEqualTo("textview")

    property.value = "textView17"
    line.refresh()
    assertThat(label.text).isEqualTo("textView17")
  }
}
