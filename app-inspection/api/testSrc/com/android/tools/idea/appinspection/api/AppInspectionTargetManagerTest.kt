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
package com.android.tools.idea.appinspection.api

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.StubTestAppInspectorClient
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.appinspection.test.TEST_PROJECT
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.CountDownLatch

class AppInspectionTargetManagerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("InspectorTargetTest", transportService, transportService)!!
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionRule)!!

  @Test
  fun launchTarget() {
    val target = appInspectionRule.launchTarget(AppInspectionTestUtils.createFakeProcessDescriptor()).get()
    assertThat(target.projectName).isEqualTo(TEST_PROJECT)
    assertThat(appInspectionRule.targetManager.targets).hasSize(1)
  }

  @Test
  fun disposesClientsForProject() {
    val terminatedProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setName("dispose").setPid(1).build()
    val terminateProcessDescriptor = AppInspectionTestUtils.createFakeProcessDescriptor(process = terminatedProcess)

    val otherProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setName("normal").setPid(2).build()
    val otherProcessDescriptor = AppInspectionTestUtils.createFakeProcessDescriptor(process = otherProcess)

    val target = appInspectionRule.launchTarget(otherProcessDescriptor, TEST_PROJECT).get()
    val disposeTarget = appInspectionRule.launchTarget(terminateProcessDescriptor, "dispose").get()
    target.launchInspector(AppInspectionTestUtils.createFakeLaunchParameters(otherProcessDescriptor, project = "normal")) {
      StubTestAppInspectorClient(it)
    }.get()
    val disposeClient = disposeTarget.launchInspector(
      AppInspectionTestUtils.createFakeLaunchParameters(terminateProcessDescriptor, project = "dispose")) {
      StubTestAppInspectorClient(it)
    }.get()

    assertThat(appInspectionRule.targetManager.targets).hasSize(2)

    val clientDisposedLatch = CountDownLatch(1)
    disposeClient.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
      override fun onDispose() {
        clientDisposedLatch.countDown()
      }
    }, MoreExecutors.directExecutor())

    appInspectionRule.targetManager.disposeClients("dispose")

    clientDisposedLatch.await()
    assertThat(appInspectionRule.targetManager.targets).hasSize(1)
    assertThat(appInspectionRule.targetManager.targets.values.first().get()).isSameAs(target)
  }

  @Test
  fun processTerminationCleansUpTarget() {
    val process = AppInspectionTestUtils.createFakeProcessDescriptor()
    appInspectionRule.launchTarget(process).get()

    assertThat(appInspectionRule.targetManager.targets).hasSize(1)

    appInspectionRule.targetManager.onProcessDisconnected(process)

    assertThat(appInspectionRule.targetManager.targets).isEmpty()
  }
}