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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBViewport
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.JPanel

class CpuListScrollPaneTest {

  @Test
  fun panelHasEmptyJBBorder() {
    val scrollPane = CpuListScrollPane(JBList<Any>(), JPanel())
    assertThat(scrollPane.border).isInstanceOf(JBEmptyBorder::class.java)
    val border = scrollPane.border as JBEmptyBorder
    assertThat(border.borderInsets).isEqualTo(JBUI.emptyInsets())
  }

  @Test
  fun viewportPassedInConstructor() {
    val viewport = JBList<Any>()
    val scrollPane = CpuListScrollPane(viewport, JPanel())
    assertThat(scrollPane.viewport.view).isEqualTo(viewport)
  }

  @Test
  fun verticalScrollbarMustBeNonOpaque() {
    val scrollPane = CpuListScrollPane(JBList<Any>(), JPanel())
    assertThat(scrollPane.verticalScrollBar.isOpaque).isFalse()
  }

  @Test
  fun createViewportCreatesJBViewportOnMac() {
    // Ignore this test if we're not in a mac environment.
    assumeTrue(SystemInfo.isMac)
    val scrollPane = CpuListScrollPane(JBList<Any>(), JPanel())
    assertThat(scrollPane.createViewport()).isInstanceOf(JBViewport::class.java)
  }

  @Test
  fun createViewportCreatesJViewportWhenNotOnMac() {
    // Ignore this test if we are in a mac environment.
    assumeFalse(SystemInfo.isMac)
    val scrollPane = CpuListScrollPane(JBList<Any>(), JPanel())
    assertThat(scrollPane.createViewport()).isNotInstanceOf(JBViewport::class.java)
  }

  @Test
  fun dispatchComponentConsumeMouseWheelIfModifierKeysArePressed() {
    val dispatchComponent = JPanel(BorderLayout())
    var dispatchComponentConsumedWheelEvent = false
    dispatchComponent.addMouseWheelListener { dispatchComponentConsumedWheelEvent = true }

    val list = JBList<Any>()
    val scrollPane = CpuListScrollPane(list, dispatchComponent)
    scrollPane.setSize(100, 100)

    val fakeUi = FakeUi(scrollPane)
    // Change the size after creating FakeUi because we call doLayout in its constructor and that will cause the list to occupy the entire
    // scrollPane. Resizing the list allows scrollPane to receive the mouse wheel event.
    list.setSize(10, 10)

    fakeUi.mouse.wheel(50, 50, -2)
    // If there is no modifier keys pressed, scrollPane should consume the mouse wheel event
    assertThat(dispatchComponentConsumedWheelEvent).isFalse()

    // If we're pressing a modifier key, such as shift, the event is going to be consumed by dispatchComponent.
    fakeUi.keyboard.press(FakeKeyboard.Key.SHIFT)
    fakeUi.mouse.wheel(50, 50, -2)
    assertThat(dispatchComponentConsumedWheelEvent).isTrue()
  }
}