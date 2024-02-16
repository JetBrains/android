/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser

import com.intellij.testFramework.HeavyPlatformTestCase
import java.awt.Dimension
import java.lang.IllegalArgumentException
import javax.swing.JPanel
import javax.swing.JTabbedPane

class HorizontalTabbedPanelBuilderTest : HeavyPlatformTestCase() {

  fun testSize() {
    run {
      // Test single component case
      val builder = HorizontalTabbedPanelBuilder()
      val aPanel = JPanel().apply {
        preferredSize = Dimension(200, 400)
      }
      builder.addTab("A", aPanel)
      val tabbedPanel = builder.build()
      val tabHeight = (tabbedPanel as JTabbedPane).getTabComponentAt(0).height
      assertEquals(200, tabbedPanel.preferredSize.width)
      assertEquals(tabHeight + 400, tabbedPanel.preferredSize.height)
    }

    run {
      // Test multiple components case. The size is maximum of components.
      val builder = HorizontalTabbedPanelBuilder()
      val aPanel = JPanel().apply {
        preferredSize = Dimension(250, 400)
      }
      val bPanel = JPanel().apply {
        preferredSize = Dimension(200, 500)
      }
      builder.addTab("A", aPanel)
      builder.addTab("B", bPanel)
      val tabbedPanel = builder.build()
      val tabHeight = (tabbedPanel as JTabbedPane).getTabComponentAt(0).height
      assertEquals(250, tabbedPanel.preferredSize.width)
      assertEquals(tabHeight + 500, tabbedPanel.preferredSize.height)
    }
  }

  fun testExceptionWhenComponentAddedTwice() {
    assertThrows(IllegalArgumentException::class.java) {
      val builder = HorizontalTabbedPanelBuilder()
      val aPanel = JPanel()
      builder.addTab("A", aPanel)
      builder.addTab("B", aPanel)
    }
  }
}
