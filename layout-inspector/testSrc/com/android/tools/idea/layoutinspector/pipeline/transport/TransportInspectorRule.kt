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
package com.android.tools.idea.layoutinspector.pipeline.transport

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.util.ConfigurationParamsBuilder
import com.android.tools.idea.layoutinspector.util.TestStringTable
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Commands.Command.CommandType.LAYOUT_INSPECTOR
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/**
 * Class with logic for creating a [TransportInspectorClient], useful for telling
 * [LayoutInspectorRule] to start up with a transport-pipeline client.
 */
class TransportClientProvider(private val transportComponents: TransportInspectorClient.TransportComponents) : InspectorClientProvider {
  override fun create(params: InspectorClientLauncher.Params, inspector: LayoutInspector): InspectorClient {
    return TransportInspectorClient(params.adb, params.process, inspector.layoutInspectorModel, inspector.stats, transportComponents)
  }
}

/**
 * Transport-pipeline specific setup and teardown for tests.
 */
class TransportInspectorRule : ExternalResource() {
  val scheduler = VirtualTimeScheduler()
  private val timer = FakeTimer()
  val transportService = FakeTransportService(timer)

  @get:Rule
  val grpcServer = FakeGrpcServer.createFakeGrpcServer("LayoutInspectorTestChannel", transportService)

  val components = TransportInspectorClient.TransportComponents(grpcServer.name, scheduler)

  /**
   * If set to true, any attempts to connect to the fake transport-pipeline layout inspector will fail
   */
  var rejectAttachRequests = false

  private val commandHandlers = mutableMapOf<
    LayoutInspectorProto.LayoutInspectorCommand.Type,
    (Commands.Command, MutableList<Common.Event>) -> Unit>()

  private val attachHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      if (command.type == ATTACH_AGENT) {
        val status = if (rejectAttachRequests) Common.AgentData.Status.UNATTACHABLE else Common.AgentData.Status.ATTACHED
        events.add(
          Common.Event.newBuilder().apply {
            pid = command.pid
            timestamp = scheduler.currentTimeNanos
            kind = Common.Event.Kind.AGENT
            agentData = Common.AgentData.newBuilder().setStatus(status).build()
          }.build()
        )
      }
    }
  }

  private val inspectorHandler: CommandHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      val handler = commandHandlers[command.layoutInspector.type]
      handler?.invoke(command, events)
    }
  }

  /**
   * Convenience method for creating a [TransportClientProvider] if you already have a
   * [TransportInspectorRule] declared in your test.
   */
  fun createClientProvider(): TransportClientProvider = TransportClientProvider(components)

  /**
   * Add a specific [LayoutInspectorProto.LayoutInspectorCommand] handler.
   *
   * This parent rule normally only supports [ATTACH_AGENT], which is needed by all tests, but
   * additionally handlers can be added as needed using this builder method. You should call it
   * at construction time.
   */
  fun addCommandHandler(type: LayoutInspectorProto.LayoutInspectorCommand.Type,
                        handler: (Commands.Command, MutableList<Common.Event>) -> Unit) {
    commandHandlers[type] = handler
  }

  override fun apply(base: Statement, description: Description): Statement {
    // Setup grpc rule before ourselves / tear down after ourselves
    return super.apply(grpcServer.apply(base, description), description)
  }

  override fun before() {
    transportService.setCommandHandler(ATTACH_AGENT, attachHandler)
    transportService.setCommandHandler(LAYOUT_INSPECTOR, inspectorHandler)
  }

  override fun after() {
    Disposer.dispose(components)
    grpcServer.channel.shutdown().awaitTermination(10, TimeUnit.SECONDS)
  }
}

/**
 * Convert a root [ViewNode] into an event that would normally be sent by the device running the
 * transport-pipeline based layout inspector.
 */
fun ViewNode?.intoComponentTreeEvent(project: Project, pid: Int, timestamp: Long, generation: Int): Common.Event {
  val strings = TestStringTable()
  val tree = TreeBuilder(strings)
  val config = ConfigurationParamsBuilder(strings)
  val rootView = this
  return Common.Event.newBuilder().apply {
    kind = Common.Event.Kind.LAYOUT_INSPECTOR
    this.timestamp = timestamp
    this.pid = pid
    groupId = Common.Event.EventGroupIds.COMPONENT_TREE.number.toLong()
    layoutInspectorEventBuilder.treeBuilder.apply {
      if (rootView != null) {
        root = tree.makeViewTree(rootView)
        addAllWindowIds(rootView.drawId)
      }
      resources = config.makeSampleContext(project).toResourceConfiguration()
      this.generation = generation
      addAllString(strings.asEntryList())
    }
  }.build()
}

/**
 * Add an event that simulates fetching a view tree (rooted with [viewRoot]) from a target, connected process.
 */
fun TransportInspectorRule.addComponentTreeEvent(inspectorRule: LayoutInspectorRule, viewRoot: ViewNode) {
  val process = inspectorRule.processes.selectedProcess ?: error("This method can only be called after a process has been connected")

  val projectRule = inspectorRule.projectRule
  val treeEvent = viewRoot.intoComponentTreeEvent(
    projectRule.project,
    process.pid,
    scheduler.currentTimeNanos,
    1)
  transportService.addEventToStream(process.streamId, treeEvent)
  scheduler.advanceBy(1100, TimeUnit.MILLISECONDS)
}
