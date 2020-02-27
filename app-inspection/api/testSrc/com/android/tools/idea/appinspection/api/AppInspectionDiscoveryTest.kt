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
package com.android.tools.idea.appinspection.api

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.test.ASYNC_TIMEOUT_MS
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AppInspectionDiscoveryTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val FAKE_PROCESS = AttachableProcessDescriptor(
    Common.Stream.newBuilder()
      .setType(Common.Stream.Type.DEVICE)
      .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
      .setDevice(FakeTransportService.FAKE_DEVICE)
      .build(),
    FakeTransportService.FAKE_PROCESS,
    AppInspectionTestUtils.TestTransportJarCopier
  )

  private val ATTACH_HANDLER = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      events.add(
        Common.Event.newBuilder()
          .setKind(Common.Event.Kind.AGENT)
          .setPid(FakeTransportService.FAKE_PROCESS.pid)
          .setAgentData(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
          .build()
      )
    }
  }

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionDiscoveryTest", transportService, transportService)!!
  private val appInspectionServiceRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionServiceRule)!!

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))
  }

  @Test
  fun attachToProcessNotifiesListener() {
    val discovery =
      AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    val targetReadyLatch = CountDownLatch(1)
    discovery.addTargetListener(appInspectionServiceRule.executorService) {
      targetReadyLatch.countDown()
    }

    discovery.attachToProcess(FAKE_PROCESS, appInspectionServiceRule.streamChannel)
  }

  @Test
  fun targetIsCached() {
    val discovery =
      AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    var target: AppInspectionTarget? = null
    val targetReadyLatch = CountDownLatch(1)
    discovery.addTargetListener(appInspectionServiceRule.executorService) {
      target = it
      targetReadyLatch.countDown()
    }

    // Attach to the process.
    val target2 = discovery.attachToProcess(FAKE_PROCESS, appInspectionServiceRule.streamChannel).get()

    // Attach to the same process again.
    val target3 = discovery.attachToProcess(FAKE_PROCESS, appInspectionServiceRule.streamChannel).get()

    targetReadyLatch.await()

    assertThat(target).isSameAs(target2)
    assertThat(target).isSameAs(target3)
  }

  @Test
  fun newListenerReceivesExistingTargets() {
    val discovery =
      AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    val targetReadyLatch = CountDownLatch(1)
    discovery.addTargetListener(appInspectionServiceRule.executorService) {
      targetReadyLatch.countDown()
    }

    discovery.attachToProcess(FAKE_PROCESS, appInspectionServiceRule.streamChannel)

    targetReadyLatch.await()

    val secondListenerLatch = CountDownLatch(1)
    discovery.addTargetListener(appInspectionServiceRule.executorService) {
      secondListenerLatch.countDown()
    }

    secondListenerLatch.await()
  }

  @Test
  fun terminatedTargetIsRemovedFromCache() {
    // Setup
    val discovery =
      AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    // Wait for 1st target to be ready
    val targetTerminatedLatch = CountDownLatch(1)
    discovery.addTargetListener(appInspectionServiceRule.executorService) {
      it.addTargetTerminatedListener(appInspectionServiceRule.executorService) {
        targetTerminatedLatch.countDown()
      }
    }
    val firstTarget = discovery.attachToProcess(FAKE_PROCESS, appInspectionServiceRule.streamChannel)

    // Fake process ended event
    transportService.addEventToStream(
      FakeTransportService.FAKE_DEVICE_ID,
      Common.Event.newBuilder()
        .setTimestamp(timer.currentTimeNs)
        .setKind(Common.Event.Kind.PROCESS)
        .setGroupId(FakeTransportService.FAKE_PROCESS.pid.toLong())
        .setPid(FakeTransportService.FAKE_PROCESS.pid)
        .setIsEnded(true)
        .build()
    )

    // Wait
    targetTerminatedLatch.await()

    val secondTarget = discovery.attachToProcess(FAKE_PROCESS, appInspectionServiceRule.streamChannel).get()

    // Verify first target was removed from discovery's internal cache, therefore the second target is newly created.
    assertThat(firstTarget).isNotSameAs(secondTarget)
  }
}