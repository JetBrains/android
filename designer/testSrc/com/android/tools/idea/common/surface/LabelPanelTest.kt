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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.swing.FakeUi
import java.awt.Dimension
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPanelTest {

  @Test
  fun `label is visible`() {
    val layoutData = LayoutData(1.0, "Name", "Tooltip", 0, 0, Dimension(10, 10))
    val label = LabelPanel(layoutData).apply { size = Dimension(250, 50) }
    FakeUi(label).also { it.layout() }
    assertTrue(label.isVisible)
  }

  @Test
  fun `label is not visible`() {
    val layoutData = LayoutData(1.0, null, null, 0, 0, Dimension(10, 10))
    val label = LabelPanel(layoutData).apply { size = Dimension(250, 50) }
    FakeUi(label).also { it.layout() }
    assertFalse(label.isVisible)
  }
}
