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
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.appinspection.internal.AppInspectionTargetManager
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AppInspectionTargetManagerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("InspectorTargetTest", transportService, transportService)!!
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionRule)!!

  @Test
  fun launchTarget() {
    appInspectionRule.launchTarget(AppInspectionTestUtils.createFakeProcessDescriptor()).get()
    assertThat(appInspectionRule.targetManager.targets).hasSize(1)
  }

  @Test
  fun disposesClientsForProject() {
    val terminatedProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setName("process1").setPid(1).build()
    val terminateProcessDescriptor = AppInspectionTestUtils.createFakeProcessDescriptor(process = terminatedProcess)

    val otherProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setName("process2").setPid(2).build()
    val otherProcessDescriptor = AppInspectionTestUtils.createFakeProcessDescriptor(process = otherProcess)

    val unfinishedProcess = terminatedProcess.toBuilder().setName("process3").setPid(3).build()
    val unfinishedProcessDescriptor = AppInspectionTestUtils.createFakeProcessDescriptor(process = unfinishedProcess)
    val unfinishedTargetFuture = SettableFuture.create<AppInspectionTarget>()

    // The makeup of this object does not matter for this test.
    val mockTarget = object : AppInspectionTarget {
      override fun launchInspector(params: AppInspectorLauncher.LaunchParameters,
                                   creator: (AppInspectorClient.CommandMessenger) -> AppInspectorClient): ListenableFuture<AppInspectorClient> {
        throw NotImplementedError()
      }

      override fun dispose() {
      }
    }

    appInspectionRule.targetManager.targets[terminateProcessDescriptor] = AppInspectionTargetManager.TargetInfo(
      Futures.immediateFuture(mockTarget), "dispose")
    appInspectionRule.targetManager.targets[unfinishedProcessDescriptor] = AppInspectionTargetManager.TargetInfo(unfinishedTargetFuture,
                                                                                                                 "dispose")
    appInspectionRule.targetManager.targets[otherProcessDescriptor] = AppInspectionTargetManager.TargetInfo(
      Futures.immediateFuture(mockTarget), "don't dispose")

    appInspectionRule.targetManager.disposeClients("dispose")

    assertThat(appInspectionRule.targetManager.targets).hasSize(1)
    assertThat(appInspectionRule.targetManager.targets.values.first().projectName).isEqualTo("don't dispose")
    assertThat(unfinishedTargetFuture.isCancelled).isTrue()
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