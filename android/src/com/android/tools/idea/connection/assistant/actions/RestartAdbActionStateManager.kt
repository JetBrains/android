/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.concurrent.EdtExecutor
import com.android.utils.HtmlBuilder
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ConnectionAssistantEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.util.AndroidBundle

/**
 * StateManager for RestartAdbAction, displays if there are any connected devices to the user through the
 * state message.
 */
class RestartAdbActionStateManager : AssistActionStateManager(), AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener, Disposable {
  private lateinit var myProject: Project
  private var myAdbFuture: ListenableFuture<AndroidDebugBridge>? = null
  private var myLoading: Boolean = false

  override fun init(project: Project, actionData: ActionData) {
    myProject = project
    initDebugBridge(myProject)
    AndroidDebugBridge.addDebugBridgeChangeListener(this)
    AndroidDebugBridge.addDeviceChangeListener(this)

    Disposer.register(project, this)

    refreshDependencyState(project)
  }

  override fun getId(): String = RestartAdbAction.ACTION_ID

  override fun getState(project: Project, actionData: ActionData): AssistActionState {
    if (myLoading) return DefaultActionState.IN_PROGRESS
    if (myAdbFuture == null) return DefaultActionState.INCOMPLETE
    if (!myAdbFuture!!.isDone) return DefaultActionState.IN_PROGRESS

    val adb = AndroidDebugBridge.getBridge()
    if (adb == null || adb.devices.isEmpty()) {
      return DefaultActionState.ERROR_RETRY
    }
    return CustomSuccessState
  }

  override fun getStateDisplay(project: Project, actionData: ActionData, message: String?): StatefulButtonMessage {
    var returnMessage = message ?: ""
    val state = getState(project, actionData)
    when (state) {
      DefaultActionState.IN_PROGRESS -> returnMessage = AndroidBundle.message("connection.assistant.loading")
      CustomSuccessState, DefaultActionState.ERROR_RETRY -> {
        val adb = AndroidDebugBridge.getBridge()

        returnMessage = if (adb != null) {
          generateMessage(adb.devices)
        }
        else {
          AndroidBundle.message("connection.assistant.adb.failure")
        }

        val deviceCount = adb?.devices?.size ?: -1
        UsageTracker.getInstance()
            .log(AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.CONNECTION_ASSISTANT_EVENT)
                .setConnectionAssistantEvent(ConnectionAssistantEvent.newBuilder()
                    .setType(ConnectionAssistantEvent.ConnectionAssistantEventType.ADB_DEVICES_DETECTED)
                    .setAdbDevicesDetected(deviceCount)))
      }
    }

    return StatefulButtonMessage(returnMessage, state)
  }

  private fun generateMessage(devices: Array<IDevice>): String {
    return if (devices.isEmpty()) {
      AndroidBundle.message("connection.assistant.adb.no_devices")
    }
    else {
      // skip open and close htmlbody because the StatefulButtonMessage will add it instead
      val builder = HtmlBuilder().addHtml("<span style=\"color: ${UIUtils.getCssColor(UIUtils.getSuccessColor())};\">"
          + AndroidBundle.message("connection.assistant.adb.devices")
          + "</span>")

      devices.forEach { device ->
        builder.addHtml("<p><span>${device.name}</span>")
            .newline()
            .addHtml("<span style=\"font-size: 80%; font-weight: lighter;\">${device.version}</span></p>")
            .newline()
      }
      builder.html
    }
  }

  private fun setLoading(loading: Boolean) {
    myLoading = loading
    refreshDependencyState(myProject)
  }

  private fun initDebugBridge(project: Project) {
    val adb = AndroidSdkUtils.getAdb(project) ?: return
    myAdbFuture = AdbService.getInstance().getDebugBridge(adb) ?: return

    Futures.addCallback(myAdbFuture, object : FutureCallback<AndroidDebugBridge> {
      override fun onSuccess(bridge: AndroidDebugBridge?) {
        refreshDependencyState(project)
      }

      override fun onFailure(t: Throwable?) {
        refreshDependencyState(project)
      }
    }, EdtExecutor.INSTANCE)
  }

  override fun dispose() {
    AndroidDebugBridge.removeDebugBridgeChangeListener(this)
    AndroidDebugBridge.removeDeviceChangeListener(this)
  }

  override fun bridgeChanged(bridge: AndroidDebugBridge?) {}

  override fun restartInitiated() {
    setLoading(true)
  }

  override fun restartCompleted(isSuccessful: Boolean) {
    setLoading(false)
  }

  override fun deviceConnected(device: IDevice) {
    refreshDependencyState(myProject)
  }

  override fun deviceDisconnected(device: IDevice) {
    refreshDependencyState(myProject)
  }

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    refreshDependencyState(myProject)
  }

}
