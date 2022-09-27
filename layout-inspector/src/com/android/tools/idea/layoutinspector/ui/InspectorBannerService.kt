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
import com.intellij.openapi.project.Project
import kotlin.properties.Delegates

class InspectorBannerService {
  val DISMISS_ACTION = object : AnAction("Dismiss") {
    override fun actionPerformed(e: AnActionEvent) {
      notification = null
    }
  }

  class LearnMoreAction(private val url: String): AnAction("Learn More") {
    override fun actionPerformed(event: AnActionEvent) {
      BrowserUtil.browse(url)
    }
  }

  val notificationListeners = mutableListOf<(StatusNotification?) -> Unit>()
  var notification: StatusNotification? by Delegates.observable(null as StatusNotification?) { _, old, new ->
    if (new != old) {
      notificationListeners.forEach { it(new) }
    }
  }

  fun setNotification(text: String, actions: List<AnAction> = listOf(DISMISS_ACTION)) {
    notification = StatusNotificationImpl(text, actions)
  }

  fun removeNotification(text: String) {
    if (notification?.message == text) {
      notification = null
    }
  }

  companion object {
    fun getInstance(project: Project): InspectorBannerService? =
      if (project.isDisposed) null else project.getService(InspectorBannerService::class.java)
  }
}