/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.ui.content.ContentManager
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.fail
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for the [computeOptimalSplitLayout] function defined in SplitLayoutOptimizer.kt. */
@Suppress("SameParameterValue")
class SplitLayoutOptimizerTest {

  @Test
  fun testHorizontalLayout1() {
    val panel1 = createMockPanel(Dimension(1200, 900), 1.0, 0, 104)
    val panel2 = createMockPanel(Dimension(1472, 3111), 1.0, 0, 39)
    val contentManager = createMockContentManager(1241, 1552, 8, 47)

    val layout = computeOptimalSplitLayout(contentManager, panel1, panel2) ?: fail("Layout was not computed")

    assertThat(layout.side).isEqualTo(PairLayout.RIGHT)
    assertThat(layout.splitRatio).isWithin(0.001f).of(0.4494f)
  }

  @Test
  fun testHorizontalLayout2() {
    val panel1 = createMockPanel(Dimension(1200, 900), 1.0, 0, 104)
    val panel2 = createMockPanel(Dimension(1472, 3111), 1.0, 0, 39)
    val contentManager = createMockContentManager(1424, 1552, 8, 47)

    val layout = computeOptimalSplitLayout(contentManager, panel1, panel2) ?: fail("Layout was not computed")

    assertThat(layout.side).isEqualTo(PairLayout.RIGHT)
    assertThat(layout.splitRatio).isWithin(0.001f).of(0.507f)
  }

  @Test
  fun testHorizontalLayout3() {
    val panel1 = createMockPanel(Dimension(1200, 900), 1.0, 0, 104)
    val panel2 = createMockPanel(Dimension(1472, 3111), 1.0, 0, 39)
    val contentManager = createMockContentManager(1952, 1552, 8, 47)

    val layout = computeOptimalSplitLayout(contentManager, panel1, panel2) ?: fail("Layout was not computed")

    assertThat(layout.side).isEqualTo(PairLayout.RIGHT)
    assertThat(layout.splitRatio).isWithin(0.001f).of(0.6325f)
  }

  @Test
  fun testVerticalLayout1() {
    val panel1 = createMockPanel(Dimension(1200, 900), 1.0, 0, 104)
    val panel2 = createMockPanel(Dimension(1472, 3111), 1.0, 0, 39)
    val contentManager = createMockContentManager(795, 1552, 8, 47)

    val layout = computeOptimalSplitLayout(contentManager, panel1, panel2) ?: fail("Layout was not computed")

    assertThat(layout.side).isEqualTo(PairLayout.BOTTOM)
    assertThat(layout.splitRatio).isWithin(0.001f).of(0.2873f)
  }

  @Test
  fun testVerticalLayout2() {
    val panel1 = createMockPanel(Dimension(1200, 900), 2.0, 0, 82)
    val panel2 = createMockPanel(Dimension(1472, 3111), 2.0, 0, 53)
    val contentManager = createMockContentManager(300, 1028, 6, 37)

    val layout = computeOptimalSplitLayout(contentManager, panel1, panel2) ?: fail("Layout was not computed")

    assertThat(layout.side).isEqualTo(PairLayout.BOTTOM)
    assertThat(layout.splitRatio).isWithin(0.001f).of(0.3074f)
  }

  private fun createMockPanel(contentSize: Dimension, scalingFactor: Double, x: Int, y: Int): EmulatorToolWindowPanel {
    val panel = mock<EmulatorToolWindowPanel>()
    val displayView = mock<EmulatorView>()
    whenever(panel.primaryDisplayView).thenReturn(displayView)
    whenever(displayView.parent).thenReturn(panel)
    whenever(displayView.x).thenReturn(x)
    whenever(displayView.y).thenReturn(y)
    whenever(displayView.naturalContentSize).thenReturn(contentSize)
    whenever(displayView.screenScalingFactor).thenReturn(scalingFactor)
    return panel
  }

  private fun createMockContentManager(decoratorWidth: Int, decoratorHeight: Int, marginWidth: Int, marginHeight: Int): ContentManager {
    val contentManager = mock<ContentManager>()
    val component = mock<JComponent>()
    val decorator = mock<InternalDecorator>()

    whenever(contentManager.component).thenReturn(component)
    whenever(component.parent).thenReturn(decorator)

    whenever(decorator.width).thenReturn(decoratorWidth)
    whenever(decorator.height).thenReturn(decoratorHeight)
    whenever(component.width).thenReturn(decoratorWidth - marginWidth)
    whenever(component.height).thenReturn(decoratorHeight - marginHeight)

    return contentManager
  }
}
