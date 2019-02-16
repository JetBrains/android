/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomLabelAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.model.FpsTimer
import com.android.tools.adtui.model.updater.Updatable
import com.android.tools.adtui.model.updater.Updater
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.profilers.ProfilerService
import com.android.tools.layoutinspector.proto.LayoutInspector.*
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.Transport.Command.CommandType.LAYOUT_INSPECTOR
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBScrollPane
import icons.StudioIcons
import java.awt.BorderLayout
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Panel that shows the device screen in the layout inspector.
 */
class DeviceViewPanel(private val layoutInspector: LayoutInspector) : JPanel(BorderLayout()), Zoomable, DataProvider, Updatable {
  enum class ViewMode(val icon: Icon) {
    FIXED(StudioIcons.LayoutEditor.Extras.ROOT_INLINE),
    X_ONLY(StudioIcons.DeviceConfiguration.SCREEN_WIDTH),
    XY(StudioIcons.DeviceConfiguration.SMALLEST_SCREEN_SIZE);

    val next: ViewMode
      get() = enumValues<ViewMode>()[(this.ordinal + 1).rem(enumValues<ViewMode>().size)]
  }

  var viewMode = ViewMode.XY

  override var scale: Double = .5

  override val screenScalingFactor = 1f

  private var drawBorders = true

  private val updater = Updater(FpsTimer(10))

  var client = ProfilerService.getInstance(layoutInspector.project)!!.profilerClient

  private val showBordersCheckBox = object : CheckboxAction("Show borders") {
    override fun isSelected(e: AnActionEvent): Boolean {
      return drawBorders
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      drawBorders = state
      repaint()
    }
  }

  private val myProcessSelectionAction = DropDownAction("Select Process", "Select a process to connect to.", StudioIcons.Common.ADD)

  private var mySelectedStream: Common.Stream? = Common.Stream.getDefaultInstance()
  private var mySelectedProcess: Common.Process? = Common.Process.getDefaultInstance()

  private var myAgentConnected = false
  private var myCaptureStarted = false
  private var myLastEventRequestTimestampNs = java.lang.Long.MIN_VALUE

  val contentPanel = DeviceViewContentPanel(layoutInspector, scale, viewMode)
  private val scrollPane = JBScrollPane(contentPanel)

  init {
    updater.register(this)

    layoutInspector.modelChangeListeners.add(::modelChanged)

    add(createToolbar(), BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)
  }

  override fun zoom(type: ZoomType): Boolean {
    val position = scrollPane.viewport.viewPosition.apply { translate(scrollPane.viewport.width / 2, scrollPane.viewport.height / 2) }
    position.x = (position.x / scale).toInt()
    position.y = (position.y / scale).toInt()
    when (type) {
      ZoomType.FIT, ZoomType.FIT_INTO, ZoomType.SCREEN -> scale = 0.5
      ZoomType.ACTUAL -> scale = 1.0
      ZoomType.IN -> scale += 0.1
      ZoomType.OUT -> scale -= 0.1
    }
    contentPanel.scale = scale
    scrollPane.viewport.revalidate()

    position.x = (position.x * scale).toInt()
    position.y = (position.y * scale).toInt()
    position.translate(-scrollPane.viewport.width / 2, -scrollPane.viewport.height / 2)
    scrollPane.viewport.viewPosition = position
    return true
  }

  override fun canZoomIn() = true

  override fun canZoomOut() = true

  override fun canZoomToFit() = true

  override fun getData(dataId: String): Any? {
    if (ZOOMABLE_KEY.`is`(dataId)) {
      return this
    }
    return null
  }

  private fun createToolbar(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, com.android.tools.adtui.common.border)!!

    val leftPanel = AdtPrimaryPanel(BorderLayout())
    val leftGroup = DefaultActionGroup()
    leftGroup.add(myProcessSelectionAction)
    leftGroup.add(showBordersCheckBox)
    leftPanel.add(ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorLeft", leftGroup, true).component,
                  BorderLayout.CENTER)
    panel.add(leftPanel, BorderLayout.CENTER)

    val rightGroup = DefaultActionGroup()
    rightGroup.add(object : AnAction("reset") {
      override fun actionPerformed(e: AnActionEvent) {
        viewMode = viewMode.next
        contentPanel.viewMode = viewMode
      }

      override fun update(e: AnActionEvent) {
        e.presentation.icon = viewMode.icon
      }
    })
    rightGroup.add(ZoomOutAction)
    rightGroup.add(ZoomLabelAction)
    rightGroup.add(ZoomInAction)
    rightGroup.add(ZoomToFitAction)
    val toolbar = ActionManager.getInstance().createActionToolbar("DynamicLayoutInspectorRight", rightGroup, true)
    toolbar.setTargetComponent(this)
    panel.add(toolbar.component, BorderLayout.EAST)
    return panel
  }

  private fun modelChanged(old: InspectorModel, new: InspectorModel) {
    scrollPane.viewport.revalidate()
    repaint()
  }

  override fun update(elapsedNs: Long) {
    // Query for current devices and processes
    val processesMap = HashMap<Common.Stream, List<Common.Process>>()
    run {
      val streams = LinkedList<Common.Stream>()
      // Get all streams of all types.
      val request = Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(-1)  // DataStoreService.DATASTORE_RESERVED_STREAM_ID
        .setKind(Common.Event.Kind.STREAM)
        .build()
      val response = client.transportClient.getEventGroups(request)
      for (group in response.groupsList) {
        val isStreamDead = group.getEvents(group.eventsCount - 1).isEnded
        if (isStreamDead) {
          // Ignore dead streams.
          continue
        }
        val connectedEvent = getLastMatchingEvent(group) { e -> e.hasStream() && e.stream.hasStreamConnected() }
                             ?: // Ignore stream event groups that do not have the connected event.
                             continue
        val stream = connectedEvent.stream.streamConnected.stream
        // We only want streams of type device to get process information.
        if (stream.type == Common.Stream.Type.DEVICE) {
          streams.add(stream)
        }
      }

      for (stream in streams) {
        val processRequest = Transport.GetEventGroupsRequest.newBuilder()
          .setStreamId(stream.streamId)
          .setKind(Common.Event.Kind.PROCESS)
          .build()
        val processResponse = client.transportClient.getEventGroups(processRequest)
        val processList = ArrayList<Common.Process>()
        // A group is a collection of events that happened to a single process.
        for (groupProcess in processResponse.groupsList) {
          val isProcessDead = groupProcess.getEvents(groupProcess.eventsCount - 1).isEnded
          if (isProcessDead) {
            // Ignore dead processes.
            continue
          }
          val aliveEvent = getLastMatchingEvent(groupProcess) { e -> e.hasProcess() && e.process.hasProcessStarted() }
                           ?: // Ignore process event groups that do not have the started event.
                           continue
          val process = aliveEvent.process.processStarted.process
          processList.add(process)
        }
        processesMap[stream] = processList
      }
    }

    refreshProcessDropdown(processesMap)

    // If a process is selected, enabled the UI once the agent is detected.
    mySelectedStream?.let { stream ->
      if (mySelectedProcess != Common.Process.getDefaultInstance()) {
        if (myAgentConnected) {
          updatePicture(stream)
        }
        else {
          tryToStartCapture(stream)
        }
      }
    }
  }

  private fun tryToStartCapture(stream: Common.Stream) {
    // Get agent data for requested session.
    val agentRequest = Transport.GetEventGroupsRequest.newBuilder()
      .setKind(Common.Event.Kind.AGENT)
      .setStreamId(mySelectedStream!!.streamId)
      .setPid(mySelectedProcess!!.pid)
      .build()
    val response = client.transportClient.getEventGroups(agentRequest)
    for (group in response.groupsList) {
      if (group.getEvents(group.eventsCount - 1).agentData.status == Common.AgentData.Status.ATTACHED) {
        myAgentConnected = true
        if (!myCaptureStarted) {
          myCaptureStarted = true
          val command = Transport.Command.newBuilder()
            .setType(LAYOUT_INSPECTOR)
            .setLayoutInspector(LayoutInspectorCommand.newBuilder().setType(LayoutInspectorCommand.Type.START))
            .setStreamId(stream.streamId)
            .setPid(mySelectedProcess!!.pid)
            .build()
          client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
          // TODO: verify that capture started successfully
        }
        break
      }
    }
  }

  private fun updatePicture(stream: Common.Stream) {
    val eventRequest = Transport.GetEventGroupsRequest.newBuilder()
      .setKind(Common.Event.Kind.LAYOUT_INSPECTOR)
      .setFromTimestamp(myLastEventRequestTimestampNs)
      .setToTimestamp(java.lang.Long.MAX_VALUE)
      .build()
    val eventResponse = client.transportClient.getEventGroups(eventRequest)
    if (eventResponse != Transport.GetEventGroupsResponse.getDefaultInstance()) {
      val events = ArrayList<Common.Event>()
      eventResponse.groupsList.forEach { group -> events.addAll(group.eventsList) }
      events.sortBy { it.timestamp }
      events.forEach { evt ->
        if (evt.timestamp >= myLastEventRequestTimestampNs) {
          System.out.println(evt.timestamp)
          if (evt.groupId == Common.Event.EventGroupIds.SKIA_PICTURE_VALUE.toLong()) {
            getPayload(stream, evt)
          }
        }
      }
      myLastEventRequestTimestampNs = Math.max(myLastEventRequestTimestampNs, events[events.size - 1].timestamp + 1)
    }
  }

  private fun getPayload(stream: Common.Stream, evt: Common.Event) {
    val application = ApplicationManager.getApplication()
    application.executeOnPooledThread {
      val bytesRequest = Transport.BytesRequest.newBuilder()
        .setStreamId(stream.streamId)
        .setId(evt.layoutInspectorEvent.payloadId.toString())
        .build()

      val bytes = client.transportClient.getBytes(bytesRequest).contents.toByteArray()
      if (bytes.isNotEmpty()) {
        SkiaParser().getViewTree(bytes)?.let {
          layoutInspector.layoutInspectorModel.root = it
          application.invokeLater {
            scrollPane.viewport.revalidate()
            repaint()
          }
        }
      }
    }
  }

  private fun buildDeviceName(device: Common.Device): String {
    val deviceNameBuilder = StringBuilder()
    val manufacturer = device.manufacturer
    var model = device.model
    val serial = device.serial
    val suffix = String.format("-%s", serial)
    if (model.endsWith(suffix)) {
      model = model.substring(0, model.length - suffix.length)
    }
    if (!StringUtil.isEmpty(manufacturer)) {
      deviceNameBuilder.append(manufacturer)
      deviceNameBuilder.append(" ")
    }
    deviceNameBuilder.append(model)

    return deviceNameBuilder.toString()
  }

  private fun refreshProcessDropdown(processesMap: Map<Common.Stream, List<Common.Process>>) {
    myProcessSelectionAction.removeAll()

    // Rebuild the action tree.
    if (processesMap.isEmpty()) {
      val noDeviceAction = object : AnAction("No devices detected") {
        override fun actionPerformed(e: AnActionEvent) {}
      }
      noDeviceAction.templatePresentation.isEnabled = false
      myProcessSelectionAction.add(noDeviceAction)
    }
    else {
      for (stream in processesMap.keys) {
        val deviceAction = DropDownAction(buildDeviceName(stream.device), null, null)
        val processes = processesMap[stream]
        if (processes == null || processes.isEmpty()) {
          val noProcessAction = object : AnAction("No debuggable processes detected") {
            override fun actionPerformed(e: AnActionEvent) {}
          }
          noProcessAction.templatePresentation.isEnabled = false
          deviceAction.add(noProcessAction)
        }
        else {
          for (process in processes) {
            val processAction = object : AnAction("${process.name} (${process.pid})") {
              override fun actionPerformed(e: AnActionEvent) {
                mySelectedStream = stream
                mySelectedProcess = process

                // The device daemon takes care of the case if and when the agent is previously attached already.
                val attachCommand = Transport.Command.newBuilder()
                  .setStreamId(mySelectedStream!!.streamId)
                  .setPid(mySelectedProcess!!.pid)
                  .setType(Transport.Command.CommandType.ATTACH_AGENT)
                  .setAttachAgent(
                    Transport.AttachAgent.newBuilder().setAgentLibFileName(String.format("libperfa_%s.so", process.getAbiCpuArch())))
                  .build()
                client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
                myAgentConnected = false
              }
            }
            deviceAction.add(processAction)
          }
        }
        myProcessSelectionAction.add(deviceAction)
      }
    }
  }

  /**
   * Helper method to return the last even in an EventGroup that matches the input condition.
   */
  private fun getLastMatchingEvent(group: Transport.EventGroup, predicate: (Common.Event) -> Boolean): Common.Event? {
    var matched: Common.Event? = null
    for (event in group.eventsList) {
      if (predicate(event)) {
        matched = event
      }
    }

    return matched
  }

}