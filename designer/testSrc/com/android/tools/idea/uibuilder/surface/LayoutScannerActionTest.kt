/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.actions.LAYOUT_SCANNER_KEY
import com.android.tools.idea.actions.NOTIFICATION_KEY
import com.android.tools.idea.common.editor.DesignSurfaceNotificationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.alwaysEnableLayoutScanner
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import org.mockito.Mockito
import java.util.concurrent.CompletableFuture

class LayoutScannerActionTest : LayoutTestCase() {

  fun testGetInstance() {
    val action = LayoutScannerAction.getInstance()
    assertNotNull(action)
  }

  fun testUpdate() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val presentation = Presentation()
    presentation.isVisible = false
    Mockito.`when`(event.presentation).thenReturn(presentation)

    val action = LayoutScannerAction.getInstance()
    action.update(event)

    assertEquals(StudioFlags.NELE_LAYOUT_SCANNER_IN_EDITOR.get(), presentation.isVisible)
  }

  fun testShowNotification() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val notificationControl = TestNotificationManager()
    Mockito.`when`(event.getData(NOTIFICATION_KEY)).thenReturn(notificationControl)

    val action = LayoutScannerAction.getInstance()
    action.actionPerformed(event)

    assertTrue(notificationControl.isNotificationShown)
    assertEquals("Running accessibility scanner...", notificationControl.notificationText)
  }

  fun testScanAndHideNotificationWhenNoResult() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val notificationControl = TestNotificationManager()
    Mockito.`when`(event.getData(NOTIFICATION_KEY)).thenReturn(notificationControl)
    Mockito.`when`(event.getData(LAYOUT_SCANNER_KEY)).thenReturn(TestLayoutScannerControl(false))

    val action = LayoutScannerAction.getInstance()
    action.actionPerformed(event)

    assertFalse(notificationControl.isNotificationShown)
    assertEquals("", notificationControl.notificationText)
  }

  fun testScanAndHideNotification() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val notificationControl = TestNotificationManager()
    Mockito.`when`(event.getData(NOTIFICATION_KEY)).thenReturn(notificationControl)
    Mockito.`when`(event.getData(LAYOUT_SCANNER_KEY)).thenReturn(TestLayoutScannerControl(true))

    val action = LayoutScannerAction.getInstance()
    action.actionPerformed(event)

    assertFalse(notificationControl.isNotificationShown)
    assertEquals("", notificationControl.notificationText)
  }

  fun testScannerConfigurationDisabled() {
    val config = LayoutScannerConfiguration.DISABLED
    assertFalse(config.isLayoutScannerEnabled)

    config.isLayoutScannerEnabled = true
    assertFalse(config.isLayoutScannerEnabled)

    config.isLayoutScannerEnabled = false
    assertFalse(config.isLayoutScannerEnabled)
  }

  fun testScannerConfig() {
    val config = LayoutScannerEnabled()
    assertFalse(config.isLayoutScannerEnabled)

    config.isLayoutScannerEnabled = true
    assertTrue(config.isLayoutScannerEnabled)

    config.isLayoutScannerEnabled = false
    assertFalse(config.isLayoutScannerEnabled)
  }

  fun testAlwaysEnabled() {
    registerApplicationService(PropertiesComponent::class.java, PropertiesComponentMock())
    val config = LayoutScannerEnabled()
    assertFalse(config.isLayoutScannerEnabled)
    alwaysEnableLayoutScanner = true
    assertTrue(config.isLayoutScannerEnabled)
    // Disabled will override alwaysEnabled.
    assertFalse(LayoutScannerConfiguration.DISABLED.isLayoutScannerEnabled)
  }

  fun testAlwaysEnabledDefault() {
    assertFalse(alwaysEnableLayoutScanner)
  }
}

private class TestLayoutScannerControl(
  private val result: Boolean) : LayoutScannerControl {
  override val scanner: NlLayoutScanner
    get() = TODO("Not yet implemented")

  override fun runLayoutScanner(): CompletableFuture<Boolean> {
    return CompletableFuture.completedFuture(result)
  }
}

private class TestNotificationManager : DesignSurfaceNotificationManager {
  var isNotificationShown = false
  var notificationText = ""
  override fun showNotification(text: String) {
    notificationText = text
    isNotificationShown = true
  }

  override fun hideNotification() {
    notificationText = ""
    isNotificationShown = false
  }
}