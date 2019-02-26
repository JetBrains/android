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
package com.android.tools.idea.layoutinspector.transport

import com.android.tools.adtui.model.FpsTimer
import com.android.tools.adtui.model.updater.Updater
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportService
import com.android.tools.layoutinspector.proto.LayoutInspector
import com.android.tools.layoutinspector.proto.LayoutInspector.LayoutInspectorCommand
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList

// TODO: This will be simplified with ag/6471450
object DefaultInspectorClient: InspectorClient {
  private var client = TransportClient(TransportService.getInstance().channelName)
  private var selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
  private var selectedProcess: Common.Process = Common.Process.getDefaultInstance()
  private var lastResponseTime = Long.MIN_VALUE
  private val callbacks = mutableMapOf<Int, (LayoutInspector.LayoutInspectorEvent) -> Unit>()
  private var agentConnected = false
  private var captureStarted = false
  private var updater = Updater(FpsTimer(10))

  init {
    // TODO: detect when a connection is dropped
    // TODO: move all communication with the agent off the UI thread
    updater.register(::update)
  }

  override fun register(groupId: Common.Event.EventGroupIds, callback: (LayoutInspector.LayoutInspectorEvent) -> Unit) {
    callbacks[groupId.number] = callback
  }

  override fun execute(command: LayoutInspector.LayoutInspectorCommand) {
    if (selectedStream == Common.Stream.getDefaultInstance() ||
        selectedProcess == Common.Process.getDefaultInstance() ||
        !agentConnected) {
      return
    }
    val transportCommand = Transport.Command.newBuilder()
      .setType(Transport.Command.CommandType.LAYOUT_INSPECTOR)
      .setLayoutInspector(command)
      .setStreamId(selectedStream.streamId)
      .setPid(selectedProcess.pid)
      .build()
    client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(transportCommand).build())
  }

  override fun getPayload(id: Int): ByteArray {
    val bytesRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(selectedStream.streamId)
      .setId(id.toString())
      .build()

    return client.transportStub.getBytes(bytesRequest).contents.toByteArray()
  }

  override fun loadProcesses(): Map<Common.Stream, List<Common.Process>> {
    // Query for current devices and processes
    val processesMap = HashMap<Common.Stream, List<Common.Process>>()
    val streams = LinkedList<Common.Stream>()
    // Get all streams of all types.
    val request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(-1)  // DataStoreService.DATASTORE_RESERVED_STREAM_ID
      .setKind(Common.Event.Kind.STREAM)
      .build()
    val response = client.transportStub.getEventGroups(request)
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
      val processResponse = client.transportStub.getEventGroups(processRequest)
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
    return processesMap
  }

  override fun attach(stream: Common.Stream, process: Common.Process) {
    // TODO: Probably need to detach from an existing process here
    selectedStream = stream
    selectedProcess = process
    agentConnected = false
    captureStarted = false

    // The device daemon takes care of the case if and when the agent is previously attached already.
    val attachCommand = Transport.Command.newBuilder()
      .setStreamId(selectedStream.streamId)
      .setPid(selectedProcess.pid)
      .setType(Transport.Command.CommandType.ATTACH_AGENT)
      .setAttachAgent(
        Transport.AttachAgent.newBuilder().setAgentLibFileName(String.format("libjvmtiagent_%s.so", process.abiCpuArch)))
      .build()
    client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
  }

  private fun tryToStartCapture() {
    if (selectedStream == Common.Stream.getDefaultInstance() ||
        selectedProcess == Common.Process.getDefaultInstance()) {
      return
    }

    // Get agent data for requested session.
    val agentRequest = Transport.GetEventGroupsRequest.newBuilder()
      .setKind(Common.Event.Kind.AGENT)
      .setStreamId(selectedStream.streamId)
      .setPid(selectedProcess.pid)
      .build()
    val response = client.transportStub.getEventGroups(agentRequest)
    for (group in response.groupsList) {
      if (group.getEvents(group.eventsCount - 1).agentData.status == Common.AgentData.Status.ATTACHED) {
        agentConnected = true
        if (!captureStarted) {
          captureStarted = true
          execute(LayoutInspectorCommand.newBuilder().setType(LayoutInspectorCommand.Type.START).build())
          // TODO: verify that capture started successfully
        }
        break
      }
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun update(elapsedNs: Long) {
    if (!agentConnected) {
      tryToStartCapture()
      return
    }
    val eventRequest = Transport.GetEventGroupsRequest.newBuilder()
      .setKind(Common.Event.Kind.LAYOUT_INSPECTOR)
      .setFromTimestamp(lastResponseTime)
      .setToTimestamp(Long.MAX_VALUE)
      .build()
    val eventResponse = client.transportStub.getEventGroups(eventRequest)
    if (eventResponse == Transport.GetEventGroupsResponse.getDefaultInstance()) {
      return
    }
    val events = ArrayList<Common.Event>()
    val handled = mutableSetOf<Int>()
    eventResponse.groupsList.forEach { group -> events.addAll(group.eventsList) }
    events.sortByDescending { it.timestamp }
    events.forEach { event ->
      lastResponseTime = Math.max(lastResponseTime, event.timestamp + 1)
      val groupId = event.groupId.toInt()
      val callback = callbacks[groupId]
      if (callback != null && !handled.contains(groupId)) {
        handled.add(groupId)
        callback.invoke(event.layoutInspectorEvent)
      }
    }
  }

  /**
   * Helper method to return the last even in an EventGroup that matches the input condition.
   */
  private fun getLastMatchingEvent(group: Transport.EventGroup, predicate: (Common.Event) -> Boolean): Common.Event? {
    return group.eventsList.lastOrNull { predicate(it) }
  }
}
