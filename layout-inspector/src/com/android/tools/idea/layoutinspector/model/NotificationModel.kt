/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel
import java.util.concurrent.atomic.AtomicReference

fun learnMoreAction(url: String) =
  StatusNotificationAction("Learn More") { BrowserUtil.browse(url) }

/** Holds the currently active notifications in a Layout Inspector session. */
class NotificationModel(val project: Project) {

  val dismissAction =
    StatusNotificationAction("Dismiss") { notification -> removeNotification(notification.id) }

  /** The current list of notifications. This list may change at any time. */
  private val notificationData = mutableListOf<StatusNotification>()

  /**
   * A copy of [notificationData]. Once retrieved the list of notification is guaranteed not to
   * change.
   */
  private val notificationList = AtomicReference<List<StatusNotification>>(emptyList())

  /** Listeners to be notified when the notifications have changed. */
  val notificationListeners = mutableListOf<() -> Unit>()

  /**
   * The current notifications.
   *
   * A copy of [notificationData] is made to avoid modifications in the middle of an extern usage.
   */
  var notifications: List<StatusNotification>
    get() = if (!isDisposed) notificationList.get() else emptyList()
    private set(value) {
      notificationList.getAndSet(value.toList())
    }

  private val isDisposed: Boolean
    get() = project.isDisposed

  /**
   * Adds a new notification.
   *
   * @param text the text of the notification.
   * @param status the kind of notification (error, warning, info)
   * @param actions the list of actions to show with this notification.
   * @param sticky if true the notification will stay until explicitly dismissed with
   *   [removeNotification].
   */
  fun addNotification(
    id: String,
    text: String,
    status: EditorNotificationPanel.Status = EditorNotificationPanel.Status.Warning,
    actions: List<StatusNotificationAction> = listOf(dismissAction),
    sticky: Boolean = false,
  ) {
    if (isDisposed) {
      return
    }
    notificationData.removeIf { it.id == id }
    notificationData.add(StatusNotification(status, id, text, sticky, actions))
    notifyChanges()
  }

  fun hasNotification(id: String): Boolean {
    return notifications.find { it.id == id } != null
  }

  fun removeNotification(id: String) {
    if (isDisposed) {
      return
    }
    if (notificationData.removeIf { it.id == id }) {
      notifyChanges()
    }
  }

  fun clear() {
    notificationData.removeIf { !it.sticky }
    notifyChanges()
  }

  private fun notifyChanges() {
    if (isDisposed) {
      return
    }
    notifications = notificationData
    notificationListeners.forEach { it() }
  }
}
