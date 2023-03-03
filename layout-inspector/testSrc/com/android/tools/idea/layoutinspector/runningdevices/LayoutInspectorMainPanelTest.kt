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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension
import javax.swing.JPanel

class LayoutInspectorMainPanelTest {

  @Test
  fun testChildrenFillParent() {
    val container = JPanel()
    val devicePanel = JPanel()
    val layoutInspectorMainPanel = LayoutInspectorMainPanel(devicePanel)

    layoutInspectorMainPanel.preferredSize = Dimension(100, 100)
    container.add(layoutInspectorMainPanel)

    // triggers a layout event
    FakeUi(container)

    assertThat(devicePanel.size).isEqualTo(Dimension(100, 100))
    assertThat(layoutInspectorMainPanel.components.size).isEqualTo(2)
    assertThat(layoutInspectorMainPanel.components[0].size).isEqualTo(Dimension(100, 100))
    assertThat(layoutInspectorMainPanel.components[1].size).isEqualTo(Dimension(100, 100))
  }
}