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

import com.google.common.annotations.VisibleForTesting
import com.android.ddmlib.AdbDevice
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.assistant.AssistActionState
import com.android.tools.idea.assistant.AssistActionStateManager
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.datamodel.DefaultActionState
import com.android.tools.idea.assistant.view.StatefulButtonMessage
import com.android.tools.idea.assistant.view.UIUtils
import com.android.tools.idea.concurrency.toCompletionStage
import com.android.tools.idea.rendering.HtmlBuilderHelper
import com.android.tools.idea.stats.withProjectId
import com.android.tools.usb.Platform
import com.android.tools.usb.UsbDeviceCollector
import com.android.tools.usb.UsbDeviceCollectorImpl
import com.android.utils.HtmlBuilder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ConnectionAssistantEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.EdtInvocationManager
import org.jetbrains.android.util.AndroidBundle
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

val Logger: Logger = com.intellij.openapi.diagnostic.Logger.getInstance(ListUsbDevicesActionStateManager::class.java)

/**
 * StateManager for {@link ListUsbDevicesAction}, displays if there are any connected USB devices to the user
 * through state message.
 */
class ListUsbDevicesActionStateManager : AssistActionStateManager(), Disposable {
  private lateinit var usbDeviceCollector: UsbDeviceCollector
  private var myProject: Project? = null
  private var myDevicesFuture = CompletableFuture.completedFuture(emptyList<DeviceCrossReference>())
  private lateinit var rawDeviceFunction: () -> CompletionStage<List<AdbDevice>>
  private lateinit var deviceFunction: () -> List<IDevice>

  companion object {
    private lateinit var myInstance: ListUsbDevicesActionStateManager

    fun getInstance(): ListUsbDevicesActionStateManager = myInstance
  }

  override fun init(project: Project, actionData: ActionData) {
    init(
      project,
      actionData,
      UsbDeviceCollectorImpl(),
      { AndroidDebugBridge.getBridge()?.rawDeviceList?.toCompletionStage() ?: CompletableFuture.completedFuture(emptyList<AdbDevice>()) },
      { AndroidDebugBridge.getBridge()?.devices?.toList() ?: emptyList() }
    )
  }

  @VisibleForTesting
  fun init(
    project: Project,
    actionData: ActionData,
    deviceCollector: UsbDeviceCollector,
    rawDeviceFunction: () -> CompletionStage<List<AdbDevice>>,
    deviceFunction: () -> List<IDevice>) {

    myProject = project
    usbDeviceCollector = deviceCollector
    myInstance = this
    this.rawDeviceFunction = rawDeviceFunction
    this.deviceFunction = deviceFunction
    refresh()
    Disposer.register(project, this)
  }

  private fun getDevices(): CompletionStage<List<DeviceCrossReference>> {
    return myDevicesFuture
  }

  fun refresh() {
    myDevicesFuture = usbDeviceCollector.listUsbDevices()
      .thenCombine(rawDeviceFunction()) { a, b -> crossReference(a, deviceFunction(), b) }
      .exceptionally {
        Logger.warn(it)
        Collections.emptyList()
      }
    myDevicesFuture
      .thenAccept {
        EdtInvocationManager.getInstance().invokeLater {
          if (!myProject!!.isDisposed) {
            UsageTracker.log(
              AndroidStudioEvent.newBuilder()
                .setKind(AndroidStudioEvent.EventKind.CONNECTION_ASSISTANT_EVENT)
                .setConnectionAssistantEvent(
                  ConnectionAssistantEvent.newBuilder()
                    .setType(ConnectionAssistantEvent.ConnectionAssistantEventType.USB_DEVICES_DETECTED)
                    .setUsbDevicesDetected(it.size)
                )
                .withProjectId(myProject)
            )
            refreshDependencyState(myProject!!)
          }
        }
      }
  }

  override fun dispose() {
    myDevicesFuture.cancel(true)
    myProject = null
  }

  override fun getState(project: Project, actionData: ActionData): AssistActionState {
    if (!myDevicesFuture.isDone) return DefaultActionState.IN_PROGRESS

    return if (myDevicesFuture.get().isEmpty()) DefaultActionState.ERROR_RETRY else CustomSuccessState
  }

  override fun getStateDisplay(project: Project, actionData: ActionData, message: String?): StatefulButtonMessage? {
    val (title, body) = generateMessage()
    return StatefulButtonMessage(title, getState(project, actionData), body)
  }

  override fun getId(): String = ListUsbDevicesAction.ACTION_ID

  private fun getAllDevices(): List<DeviceCrossReference> {
    return myDevicesFuture.get()
  }

  private fun generateMessage(): ButtonMessage {
    if (!myDevicesFuture.isDone) return ButtonMessage("Loading...")

    val devices = getAllDevices().map {
      summarize(it)
    }.sortedBy { it.label }

    val titleHtmlBuilder = HtmlBuilder().openHtmlBody()
    if (devices.isNotEmpty()) {
      titleHtmlBuilder
        .beginSpan("color: " + UIUtils.getCssColor(UIUtils.getSuccessColor()))
        .add("Android Studio detected ${devices.size} device(s).")
        .endSpan()
    }
    else {
      titleHtmlBuilder
        .beginSpan("color: " + UIUtils.getCssColor(UIUtils.getFailureColor()))
        .add(AndroidBundle.message("connection.assistant.usb.no_devices.title"))
        .endSpan()
    }

    val workingDevices = devices.filter { it.section == ConnectionAssistantSection.WORKING }
    val problemDevices = devices.filter { it.section == ConnectionAssistantSection.POSSIBLE_PROBLEM }
    val usbDevices = devices.filter { it.section == ConnectionAssistantSection.OTHER_USB }

    val bodyHtmlBuilder = HtmlBuilder().openHtmlBody()

    bodyHtmlBuilder.newline()

    if (workingDevices.isNotEmpty()) {
      bodyHtmlBuilder
        .addHeading("Found ${workingDevices.size} Android device(s) ready for debugging:", HtmlBuilderHelper.getHeaderFontColor())

      workingDevices.forEach { device ->
        val name = device.label
        bodyHtmlBuilder
          .beginParagraph()
          .add(name)
        bodyHtmlBuilder.newlineIfNecessary().endParagraph()
      }
      bodyHtmlBuilder.beginParagraph().endParagraph()
    }

    if (problemDevices.isNotEmpty()) {
      bodyHtmlBuilder
        .addHeading("Found ${problemDevices.size} Android device(s) with possible problems:", HtmlBuilderHelper.getHeaderFontColor())

      problemDevices.forEach { device ->
        val name = device.label
        bodyHtmlBuilder
          .beginParagraph().add(name).endParagraph()
        if (device.errorMessage != null) {
          bodyHtmlBuilder
            .beginList()
            .listItem().add(device.errorMessage)
            .endList()
        }
      }
      bodyHtmlBuilder.beginParagraph().endParagraph()
    }

    if (usbDevices.isNotEmpty()) {
      bodyHtmlBuilder
        .addHeading("Found ${usbDevices.size} USB device(s) not recognized as Android devices:", HtmlBuilderHelper.getHeaderFontColor())
      // Instead of displaying multiple devices of the same name, merge them into one and display the count
      usbDevices.groupBy { usbDevice -> usbDevice }.forEach { _, deviceList ->
        val device = deviceList.first()
        val name = device.label

        bodyHtmlBuilder
          .beginParagraph()
          .add(name)

        if (deviceList.size > 1) {
          bodyHtmlBuilder.addNbsp().addItalic("(${deviceList.size} devices)")
        }

        bodyHtmlBuilder.newlineIfNecessary().endParagraph()
      }
      bodyHtmlBuilder.beginParagraph().endParagraph()
    }

    if (devices.isEmpty()) {
      bodyHtmlBuilder
        .beginParagraph()
        .add(AndroidBundle.message("connection.assistant.usb.no_devices.body"))
        .endParagraph()
        .newlineIfNecessary()
    }

    if (usbDeviceCollector.getPlatform() == Platform.Windows) {
      bodyHtmlBuilder
        .beginParagraph()
        .addBold("Install device drivers.")
        .add(" If you want to connect a device for testing, then you need to " +
             "install the appropriate USB drivers. For more information, read the ")
        .addLink("online documentation", "https://developer.android.com/studio/run/oem-usb.html")
        .add(".")
        .endParagraph()
    }

    return ButtonMessage(titleHtmlBuilder.closeHtmlBody().html, bodyHtmlBuilder.closeHtmlBody().html)
  }

}
