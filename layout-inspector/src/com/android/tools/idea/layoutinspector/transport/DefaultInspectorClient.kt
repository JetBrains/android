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

import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorEvent
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.google.common.util.concurrent.MoreExecutors
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList
import java.util.concurrent.TimeUnit

object DefaultInspectorClient : InspectorClient {
  private var client = TransportClient(TransportService.getInstance().channelName)
  private var transportPoller = TransportEventPoller.createPoller(client.transportStub,
                                                                  TimeUnit.MILLISECONDS.toNanos(100),
                                                                  Comparator.comparing(Common.Event::getTimestamp).reversed())

  private var selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
  private var selectedProcess: Common.Process = Common.Process.getDefaultInstance()
  private var agentConnected = false
  private var captureStarted = false

  private val lastResponseTimePerGroup = mutableMapOf<Long, Long>()

  // TODO: detect when a connection is dropped
  // TODO: move all communication with the agent off the UI thread

  override fun register(groupId: Common.Event.EventGroupIds, callback: (LayoutInspectorEvent) -> Unit) {
    // TODO: unregister listeners
    transportPoller.registerListener(TransportEventListener(
      eventKind = Common.Event.Kind.LAYOUT_INSPECTOR,
      executor = MoreExecutors.directExecutor(),
      streamId = selectedStream::getStreamId,
      groupId = { groupId.number.toLong() },
      processId = selectedProcess::getPid) {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance() && agentConnected &&
          it.timestamp > lastResponseTimePerGroup.getOrDefault(it.groupId, 0)) {
        callback(it.layoutInspectorEvent)
        lastResponseTimePerGroup[it.groupId] = it.timestamp
      }
    })
  }

  override fun execute(command: LayoutInspectorCommand) {
    if (selectedStream == Common.Stream.getDefaultInstance() ||
        selectedProcess == Common.Process.getDefaultInstance() ||
        !agentConnected) {
      return
    }
    val transportCommand = Command.newBuilder()
      .setType(Command.CommandType.LAYOUT_INSPECTOR)
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
    val attachCommand = Command.newBuilder()
      .setStreamId(selectedStream.streamId)
      .setPid(selectedProcess.pid)
      .setType(Command.CommandType.ATTACH_AGENT)
      .setAttachAgent(
        Commands.AttachAgent.newBuilder()
          .setAgentLibFileName(String.format("libjvmtiagent_%s.so", process.abiCpuArch))
          .setAgentConfigPath(TransportFileManager.getAgentConfigFile()))
      .build()

    lateinit var listener: TransportEventListener
    listener = TransportEventListener(
      eventKind = Common.Event.Kind.AGENT,
      executor = MoreExecutors.directExecutor(),
      streamId = stream::getStreamId,
      processId = process::getPid,
      filter = { it.agentData.status == Common.AgentData.Status.ATTACHED }
    ) {
      agentConnected = true
      execute(LayoutInspectorCommand.newBuilder().setType(LayoutInspectorCommand.Type.START).build())
      // TODO: verify that capture started successfully

      transportPoller.unregisterListener(listener)
    }
    transportPoller.registerListener(listener)

    client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build())
  }

  /**
   * Helper method to return the last even in an EventGroup that matches the input condition.
   */
  private fun getLastMatchingEvent(group: Transport.EventGroup, predicate: (Common.Event) -> Boolean): Common.Event? {
    return group.eventsList.lastOrNull { predicate(it) }
  }
}
