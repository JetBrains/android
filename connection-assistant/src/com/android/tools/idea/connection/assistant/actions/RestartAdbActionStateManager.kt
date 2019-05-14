/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.assistant.AssistActionState
import com.android.tools.idea.assistant.AssistActionStateManager
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.datamodel.DefaultActionState
import com.android.tools.idea.assistant.view.StatefulButtonMessage
import com.android.tools.idea.assistant.view.UIUtils
import com.android.tools.idea.stats.withProjectId
import com.android.utils.HtmlBuilder
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ConnectionAssistantEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.util.AndroidBundle

/**
 * StateManager for RestartAdbAction, displays if there are any connected devices to the user through the
 * state message.
 */
class RestartAdbActionStateManager : AssistActionStateManager() {

  private val projectStates = mutableMapOf<Project, State>()

  private inner class State(val project: Project) : AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener, Disposable {
    private var adbFuture: ListenableFuture<AndroidDebugBridge>? = null
    private var loading = false

    init {
      AndroidDebugBridge.addDebugBridgeChangeListener(this)
      AndroidDebugBridge.addDeviceChangeListener(this)
      Disposer.register(project, this)
      initDebugBridge()
    }

    fun initDebugBridge() {
      val adb = AndroidSdkUtils.getAdb(project) ?: return
      adbFuture = AdbService.getInstance().getDebugBridge(adb) ?: return

      Futures.addCallback(adbFuture, object : FutureCallback<AndroidDebugBridge> {
        override fun onSuccess(bridge: AndroidDebugBridge?) {
          refreshDependencyState(project)
        }

        override fun onFailure(t: Throwable?) {
          refreshDependencyState(project)
        }
      }, EdtExecutorService.getInstance())
    }

    private fun setLoading(loading: Boolean) {
      this.loading = loading
      refreshDependencyState(project)
    }

    override fun bridgeChanged(bridge: AndroidDebugBridge?) {}

    override fun restartInitiated() {
      setLoading(true)
    }

    override fun restartCompleted(isSuccessful: Boolean) {
      setLoading(false)
    }

    override fun deviceConnected(device: IDevice) {
        refreshDependencyState(project)
    }

    override fun deviceDisconnected(device: IDevice) {
      refreshDependencyState(project)
    }

    override fun deviceChanged(device: IDevice, changeMask: Int) {
      refreshDependencyState(project)
    }

    override fun dispose() {
      AndroidDebugBridge.removeDebugBridgeChangeListener(this)
      AndroidDebugBridge.removeDeviceChangeListener(this)
      projectStates.remove(project)
    }

    fun getAssistActionState(): AssistActionState {
      if (loading) return DefaultActionState.IN_PROGRESS
      if (adbFuture == null) return DefaultActionState.INCOMPLETE
      if (!adbFuture!!.isDone) return DefaultActionState.IN_PROGRESS

      val adb = AndroidDebugBridge.getBridge()
      if (adb == null || adb.devices.isEmpty()) {
        return DefaultActionState.ERROR_RETRY
      }
      return CustomSuccessState
    }

    fun getStateDisplay(): StatefulButtonMessage {
      val state = getAssistActionState()
      val (title, body) = when (state) {
        DefaultActionState.IN_PROGRESS -> ButtonMessage(AndroidBundle.message("connection.assistant.loading"))
        CustomSuccessState, DefaultActionState.ERROR_RETRY -> {
          val adb = AndroidDebugBridge.getBridge()

          val deviceCount = adb?.devices?.size ?: -1
          UsageTracker.log(
            AndroidStudioEvent.newBuilder()
              .setKind(AndroidStudioEvent.EventKind.CONNECTION_ASSISTANT_EVENT)
              .setConnectionAssistantEvent(ConnectionAssistantEvent.newBuilder()
                                             .setType(ConnectionAssistantEvent.ConnectionAssistantEventType.ADB_DEVICES_DETECTED)
                                             .setAdbDevicesDetected(deviceCount))
              .withProjectId(project))

          if (adb != null) {
            generateMessage(adb.devices)
          }
          else {
            ButtonMessage(AndroidBundle.message("connection.assistant.adb.failure"))
          }

        }
        else -> {
          ButtonMessage(AndroidBundle.message("connection.assistant.adb.unexpected"))
        }
      }

      return StatefulButtonMessage(title, state, body)
    }
  }

  override fun init(project: Project, actionData: ActionData) {
    projectStates.computeIfAbsent(project, ::State)
    refreshDependencyState(project)
  }

  override fun getId(): String = RestartAdbAction.ACTION_ID

  override fun getState(project: Project, actionData: ActionData) =
    projectStates[project]?.getAssistActionState() ?: throw IllegalStateException("getState called before init for this project")

  override fun getStateDisplay(project: Project, actionData: ActionData, message: String?) =
    projectStates[project]?.getStateDisplay() ?: throw IllegalStateException("getStateDisplay called before init for this project")

  private fun generateMessage(devices: Array<IDevice>): ButtonMessage {
    return if (devices.isEmpty()) {
      ButtonMessage(
        HtmlBuilder().addHtml(AndroidBundle.message("connection.assistant.adb.no_devices.title")).newlineIfNecessary().html,
        HtmlBuilder().addHtml("<p>${AndroidBundle.message("connection.assistant.adb.no_devices.body")}</p>").html)
    }
    else {
      val title = HtmlBuilder().addHtml("<span style=\"color: ${UIUtils.getCssColor(UIUtils.getSuccessColor())};\">"
          + AndroidBundle.message("connection.assistant.adb.devices")
          + "</span>").html

      val htmlBodyBuilder = HtmlBuilder()
      devices.forEach { device ->
        htmlBodyBuilder.addHtml("<p><span>${device.name}</span>")
            .newline()
            .addHtml("<span style=\"font-size: 80%; font-weight: lighter;\">${device.version}</span></p>")
            .newline()
      }
      ButtonMessage(title, htmlBodyBuilder.html)
    }
  }

}
