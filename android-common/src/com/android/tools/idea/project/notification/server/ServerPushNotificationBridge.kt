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
package com.android.tools.idea.project.notification.server

import com.android.tools.idea.serverflags.protos.StudioPushNotification
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * A bridge interface that decouples the server-driven push notifications framework from AI-specific implementations (such as Gemini user
 * tiers and UI navigation).
 *
 * High-level consumer modules (like aiplugin) must implement this interface and register it as an extension to connect the platform engine
 * to their feature checks.
 */
interface ServerPushNotificationBridge {
  /**
   * Evaluates a requirement.
   * - Returns `true` if handled and satisfied.
   * - Returns `false` if handled and unfulfilled.
   * - Returns `null` if unhandled by this bridge.
   */
  fun checkRequirement(requirement: Requirement): Boolean?

  /**
   * Renders and displays the given [notification].
   *
   * @param notification The push notification campaign to render (e.g. Balloon or GotItTooltip).
   * @param listener Callback handle that MUST be invoked by individual bridge implementations when UI interaction events occur:
   *     - [ServerPushNotificationListener.onNotificationShown] when the UI popup/toast is rendered on screen.
   *     - [ServerPushNotificationListener.onNotificationDismissed] when the UI popup/toast is closed or expired.
   *     - [ServerPushNotificationListener.onNotificationActionClicked] when a CTA action link or button is clicked.
   *
   * @return `true` if this bridge handled and rendered the notification, or `false` to allow other bridges in the extension chain to try.
   */
  fun showNotification(notification: StudioPushNotification, listener: ServerPushNotificationListener): Boolean

  companion object {
    private val EP_NAME =
      ExtensionPointName.create<ServerPushNotificationBridge>("com.android.tools.idea.project.notification.server.bridge")

    fun getBridges(project: Project): List<ServerPushNotificationBridge> = EP_NAME.getExtensions(project).toList()
  }
}

/**
 * Helper function to execute an IDE action ID for a notification CTA button click. Injects key-value DataContext parameters from protobuf
 * server flags into the action's DataContext.
 */
fun executeIdeAction(
  project: Project,
  actionId: String,
  dataContextParams: Map<String, String> = emptyMap(),
) {
  if (ApplicationManager.getApplication().isUnitTestMode) return
  val actionManager = ActionManager.getInstance()
  val actionToExecute = actionManager.getAction(actionId)
  if (actionToExecute != null) {
    val builder = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
    dataContextParams.forEach { (key, value) ->
      if (key.isNotBlank() && value.isNotBlank()) {
        builder.add(DataKey.create<String>(key), value)
      }
    }
    val dataContext = builder.build()
    val event = AnActionEvent.createEvent(actionToExecute, dataContext, null, ActionPlaces.NOTIFICATION, ActionUiKind.NONE, null)
    ActionUtil.performAction(actionToExecute, event)
  } else {
    Logger.getInstance(ServerPushNotificationBridge::class.java).warn("Action with ID $actionId not found")
  }
}
