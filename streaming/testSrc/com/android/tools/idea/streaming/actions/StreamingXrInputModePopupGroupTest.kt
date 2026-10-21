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
package com.android.tools.idea.streaming.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.DEVICE_TYPE_KEY
import com.android.tools.idea.streaming.core.FLOATING_TOOLBAR_KEY
import com.android.tools.idea.streaming.core.FloatingToolbarContainer
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.testutil.newEmulatorView
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_E
import javax.swing.JPanel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class StreamingXrInputModePopupGroupTest {
  private val disposableRule = DisposableRule()
  private val emulatorViewRule = EmulatorViewRule()
  private val popupRule = JBPopupRule()

  @get:Rule val rule = RuleChain(ApplicationRule(), disposableRule, emulatorViewRule, EdtRule(), popupRule)

  private val project
    get() = emulatorViewRule.project

  private var testRootDisposable = Disposer.newDisposable()

  @Before
  fun setUp() {
    testRootDisposable = Disposer.newDisposable()
    StudioFlags.EMBEDDED_EMULATOR_XR_HAND_AND_EYE_INPUT.overrideForTest(true, testRootDisposable)
  }

  @After
  fun tearDown() {
    Disposer.dispose(testRootDisposable)
  }

  @Test
  fun testCollapsibleToolbarInteraction() {
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createXrHeadsetAvd)
    val group = StreamingXrInputModePopupGroup()
    // Add some children to the group so childrenCount > 1
    group.add(StreamingXrInputModeAction.InteractionHand().apply { templatePresentation.text = "Hand" })
    group.add(StreamingXrInputModeAction.InteractionEye().apply { templatePresentation.text = "Eye" })

    // 1. Initially inactive collapsible toolbar
    val container = FloatingToolbarContainer(horizontal = true, collapsedStateSelector = { true }, initiallyActive = false)
    container.setTargetComponent(view)

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(DEVICE_TYPE_KEY, DeviceType.XR_HEADSET)
        .add(EMULATOR_CONTROLLER_KEY, view.emulator)
        .add(FLOATING_TOOLBAR_KEY, container)
        .build()

    val event = createTestEvent(group, dataContext)

    group.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isPopupGroup).isFalse()

    group.actionPerformed(event)
    val fromEvent = FloatingToolbarContainer.fromActionEvent(event)
    assertThat(fromEvent).isSameAs(container)
    assertThat(container.isActive).isTrue()
    assertThat(popupRule.fakePopupFactory.popupCount).isEqualTo(0)

    // 2. Active collapsible toolbar
    group.update(event)
    assertThat(event.presentation.isPopupGroup).isTrue()

    group.actionPerformed(event)
    assertThat(popupRule.fakePopupFactory.popupCount).isEqualTo(1)
  }

  @Test
  fun testNonCollapsibleToolbarInteraction() {
    val view = emulatorViewRule.newEmulatorView(FakeEmulator::createXrHeadsetAvd)
    val group = StreamingXrInputModePopupGroup()
    group.add(StreamingXrInputModeAction.InteractionHand().apply { templatePresentation.text = "Hand" })
    group.add(StreamingXrInputModeAction.InteractionEye().apply { templatePresentation.text = "Eye" })

    // Non-collapsible toolbar (collapsedStateSelector = null)
    val container = FloatingToolbarContainer(horizontal = true, collapsedStateSelector = null, initiallyActive = false)
    container.setTargetComponent(view)

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(DEVICE_TYPE_KEY, DeviceType.XR_HEADSET)
        .add(EMULATOR_CONTROLLER_KEY, view.emulator)
        .add(FLOATING_TOOLBAR_KEY, container)
        .build()

    val event = createTestEvent(group, dataContext)

    group.update(event)
    assertThat(event.presentation.isPopupGroup).isTrue()

    group.actionPerformed(event)
    assertThat(popupRule.fakePopupFactory.popupCount).isEqualTo(1)
  }

  private fun createTestEvent(group: StreamingXrInputModePopupGroup, dataContext: DataContext): AnActionEvent {
    val inputEvent = KeyEvent(JPanel(), KEY_RELEASED, System.currentTimeMillis(), 0, VK_E, CHAR_UNDEFINED)
    return AnActionEvent.createEvent(dataContext, group.templatePresentation.clone(), ActionPlaces.TOOLBAR, ActionUiKind.NONE, inputEvent)
  }
}
