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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.model.StatusNotification
import com.android.tools.idea.layoutinspector.model.StatusNotificationImpl
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.AtomicReference

val NOTIFICATION_KEY = DataKey.create<StatusNotification>(StatusNotification::class.java.name)

class InspectorBannerService {
  val DISMISS_ACTION = object : AnAction("Dismiss") {
    override fun actionPerformed(event: AnActionEvent) {
      val notification = event.getData(NOTIFICATION_KEY) ?: return
      removeNotification(notification.message)
    }
  }

  class LearnMoreAction(private val url: String): AnAction("Learn More") {
    override fun actionPerformed(event: AnActionEvent) {
      BrowserUtil.browse(url)
    }
  }

  /**
   * The current list of notifications. This list may change at any time.
   */
  private val notificationData = mutableListOf<StatusNotification>()

  /**
   * A copy of [notificationData]. Once retrieved the list of notification is guaranteed not to change.
   */
  private val notificationList = AtomicReference<List<StatusNotification>>(emptyList())

  /**
   * Listeners to be notified when the notifications have changed.
   */
  val notificationListeners = mutableListOf<() -> Unit>()

  /**
   * The current notifications.
   *
   * A copy of [notificationData] is made to avoid modifications in the middle of an extern usage.
   */
  var notifications: List<StatusNotification>
    get() = notificationList.get()
    private set(value) {
      notificationList.getAndSet(value.toList())
    }

  fun addNotification(text: String, actions: List<AnAction> = listOf(DISMISS_ACTION)) {
    if (notificationData.any { it.message == text }) {
      return
    }
    notificationData.add(StatusNotificationImpl(text, actions))
    notifyChanges()
  }

  fun removeNotification(text: String) {
    if (notificationData.removeIf { it.message == text }) {
      notifyChanges()
    }
  }

  fun clear() {
    notificationData.clear()
    notifyChanges()
  }

  private fun notifyChanges() {
    notifications = notificationData
    notificationListeners.forEach { it() }
  }

  companion object {
    fun getInstance(project: Project): InspectorBannerService? =
      if (project.isDisposed) null else project.getService(InspectorBannerService::class.java)
  }
}
