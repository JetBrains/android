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
package com.android.tools.adtui.ui

import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.util.preferredHeight
import java.awt.Container
import javax.swing.JPanel
import org.junit.Rule
import org.junit.Test

/** Tests for [NotificationHolderPanel]. */
@RunsInEdt
class NotificationHolderPanelTest {

  private val disposableRule = DisposableRule()
  @get:Rule val rule = RuleChain(ApplicationRule(), disposableRule, EdtRule())

  @Test
  fun testShowFadeOutNotificationHeightAccommodatesContainedLabel() {
    val content = JPanel()
    val notificationHolderPanel = NotificationHolderPanel(content)
    notificationHolderPanel.setBounds(0, 0, 200, 500)
    val ui = FakeUi(notificationHolderPanel, createFakeWindow = true, disposableRule.disposable)

    notificationHolderPanel.showFadeOutNotification("Notification message")
    ui.layoutAndDispatchEvents()
    val popup = notificationHolderPanel.getComponent(0) as Container
    val insets = popup.insets
    val expectedHeight = EditorNotificationPanel().apply { text = "Notification message" }.preferredHeight + insets.top + insets.bottom
    assertThat(popup.height).isEqualTo(expectedHeight)
  }
}
