/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import javax.swing.JPanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ZoomableContainerTest {

  private val projectRule = AndroidProjectRule.inMemory().onEdt()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private lateinit var contentPanel: JPanel
  private lateinit var container: ZoomableContainer
  private var zoomPercent = 50

  @Before
  fun setUp() {
    contentPanel = JPanel()
    // Reset zoom before each test
    zoomPercent = 50
    container =
      ZoomableContainer(
        disposable = projectRule.testRootDisposable,
        contentPanel = contentPanel,
        getZoomPercent = { zoomPercent },
        setZoomPercent = { zoomPercent = it },
      )
  }

  @Test
  fun testZoomInAndOut() {
    // Initial state
    assertThat(container.scale).isEqualTo(0.5)

    // Zoom In (50 -> 60)
    assertThat(container.canZoomIn()).isTrue()
    val zoomedIn = container.zoom(ZoomType.IN)
    assertThat(zoomedIn).isTrue()
    assertThat(zoomPercent).isEqualTo(60)

    // Zoom Out (60 -> 50)
    assertThat(container.canZoomOut()).isTrue()
    val zoomedOut = container.zoom(ZoomType.OUT)
    assertThat(zoomedOut).isTrue()
    assertThat(zoomPercent).isEqualTo(50)
  }

  @Test
  fun testZoomClamping() {
    // Test Max Clamp (100%)
    zoomPercent = 100
    assertThat(container.canZoomIn()).isFalse()
    val zoomedIn = container.zoom(ZoomType.IN)
    assertThat(zoomedIn).isFalse()
    assertThat(zoomPercent).isEqualTo(100)

    // Test Min Clamp (10%)
    zoomPercent = 10
    assertThat(container.canZoomOut()).isFalse()
    val zoomedOut = container.zoom(ZoomType.OUT)
    assertThat(zoomedOut).isFalse()
    assertThat(zoomPercent).isEqualTo(10)
  }

  @Test
  fun testZoomToActual() {
    zoomPercent = 50
    assertThat(container.canZoomToActual()).isTrue()

    container.zoom(ZoomType.ACTUAL)

    assertThat(zoomPercent).isEqualTo(100)
    assertThat(container.canZoomToActual()).isFalse()
  }

  @Test
  fun testZoomToFitCalculation() {
    val fakeUi = FakeUi(container)
    container.setSize(600, 600)
    fakeUi.layout()

    // Set preferred size to affect zoom to fit calculation
    contentPanel.preferredSize = Dimension(1000, 1000)

    // Set initial zoom to something else to ensure it changes
    zoomPercent = 100

    assertThat(container.canZoomToFit()).isTrue()
    container.zoom(ZoomType.FIT)

    assertThat(zoomPercent).isEqualTo(60)
  }

  @Test
  fun testZoomToFitRoundsToStep() {
    val fakeUi = FakeUi(container)
    container.setSize(600, 600)
    fakeUi.layout()

    // Set preferred size to affect zoom to fit calculation
    contentPanel.preferredSize = Dimension(980, 980)

    // Set initial zoom to something else to ensure it changes
    zoomPercent = 100

    container.zoom(ZoomType.FIT)
    assertThat(zoomPercent).isEqualTo(60)
  }
}
