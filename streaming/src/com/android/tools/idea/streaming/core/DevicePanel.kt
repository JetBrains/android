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

import com.android.tools.idea.streaming.emulator.DisplayViewContainer
import com.intellij.openapi.Disposable
import com.intellij.ui.EditorNotificationPanel
import javax.swing.JComponent

interface DevicePanel<T : DisplayViewContainer<*>> : DisplayOwner, Disposable {

  val deviceSerialNumber: String

  val component: JComponent
    get() = this as JComponent

  /**
   * Adds a notification panel. If the [notificationPanel] has a close action, that action has to make sure that the notification is removed
   * when the action is executed.
   */
  fun addNotification(notificationPanel: EditorNotificationPanel)

  /** Removes the given notification panel. */
  fun removeNotification(notificationPanel: EditorNotificationPanel)
}
