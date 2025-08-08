/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

/** Test for [EmptyStatePanel]. */
@RunsInEdt
class EmptyStatePanelTest {

  private val projectRule = ProjectRule()
  @get:Rule val rule = RuleChain(projectRule, EdtRule())

  private val testRootDisposable
    get() = projectRule.disposable
  private val emptyStatePanel by lazy { createEmptyStatePanel() }
  private val ui by lazy { FakeUi(emptyStatePanel) }

  @After
  fun tearDown() {
    Disposer.dispose(emptyStatePanel)
    EmulatorSettings.getInstance().loadState(EmulatorSettings())
    DeviceMirroringSettings.getInstance().loadState(DeviceMirroringSettings())
  }

  @Test
  fun testActivateOnConnectionEnabled() {
    DeviceMirroringSettings.getInstance().activateOnConnection = true
    val htmlComponent = ui.getComponent<JEditorPane>()
    assertThat(htmlComponent.normalizedText).contains("To mirror a physical device, connect it via USB cable or over WiFi.")
  }

  @Test
  fun testActivateOnConnectionDisabled() {
    DeviceMirroringSettings.getInstance().activateOnConnection = false
    val htmlComponent = ui.getComponent<JEditorPane>()
    assertThat(htmlComponent.normalizedText).contains(
        "To mirror a physical device, connect it via USB cable or over WiFi, click" +
        " <font color=\"6c707e\" size=\"+1\"><b>&#65291;</b></font> and select the device from the list." +
        " You may also select the <b>Activate mirroring when a new physical device is connected</b> option in the" +
        " <font color=\"589df6\"><a href=\"DeviceMirroringSettings\">Device Mirroring settings</a></font>.")
  }

  @Test
  fun testLaunchInToolWindowEnabled() {
    EmulatorSettings.getInstance().launchInToolWindow = true
    val mockToolWindow = mock<ToolWindow>()
    val mockToolWindowManager = mock<ToolWindowManager>()
    whenever(mockToolWindowManager.getToolWindow("Device Manager 2")).thenReturn(mockToolWindow)
    projectRule.project.replaceService(ToolWindowManager::class.java, mockToolWindowManager, testRootDisposable)
    val htmlComponent = ui.getComponent<JEditorPane>()
    assertThat(htmlComponent.normalizedText).contains(
        "To launch a virtual device, click <font color=\"6c707e\" size=\"+1\"><b>&#65291;</b></font> and select the device from the list," +
        " or use the <font color=\"589df6\"><a href=\"DeviceManager\">Device Manager</a></font>.")
    htmlComponent.clickOnHyperlink("DeviceManager")
    verify(mockToolWindow, times(1)).show(anyOrNull())
  }

  @Test
  fun testLaunchInToolWindowDisabled() {
    EmulatorSettings.getInstance().launchInToolWindow = false
    val htmlComponent = ui.getComponent<JEditorPane>()
    assertThat(htmlComponent.normalizedText).contains(
        "To launch a virtual device, click <font color=\"6c707e\" size=\"+1\"><b>&#65291;</b></font> and select a virtual device," +
        " or select the <b>Launch in the Running Devices tool window</b> option in the" +
        " <font color=\"589df6\"><a href=\"EmulatorSettings\">Emulator settings</a></font>" +
        " and use the <font color=\"589df6\"><a href=\"DeviceManager\">Device Manager</a></font>.")
  }

  private fun createEmptyStatePanel(): EmptyStatePanel =
      EmptyStatePanel(projectRule.project, testRootDisposable).apply { setSize(500, 1000) }

  private fun JEditorPane.clickOnHyperlink(hyperlink: String) {
    fireHyperlinkUpdate(HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, hyperlink))
  }

  private val JEditorPane.normalizedText: String
    get() = text.replace(Regex("&#160;|\\s+"), " ")
}