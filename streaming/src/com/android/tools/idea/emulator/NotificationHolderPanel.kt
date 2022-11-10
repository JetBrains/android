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
package com.android.tools.idea.emulator

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel

/**
 * A panel that can display a notification at the top.
 */
class NotificationHolderPanel : BorderLayoutPanel() {
  private val notificationPanel = EditorNotificationPanel(HintUtil.INFORMATION_COLOR_KEY)
  private val notificationBar = BorderLayoutPanel().apply {
    border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.BOTTOM)
    addToCenter(notificationPanel)
  }
  private var notificationVisible = false

  init {
    border = JBUI.Borders.empty()
  }

  fun showNotification(text: String) {
    notificationPanel.text = text
    if (!notificationVisible) {
      addToTop(notificationBar)
      notificationVisible = true
    }
    validate()
  }

  fun hideNotification() {
    if (notificationVisible) {
      remove(notificationBar)
      notificationVisible = false
    }
    validate()
  }
}