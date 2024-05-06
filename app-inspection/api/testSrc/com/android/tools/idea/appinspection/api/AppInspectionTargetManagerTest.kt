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
import com.android.tools.idea.appinspection.inspector.api.awaitForDisposal
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.appinspection.test.TEST_PROJECT
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AppInspectionTargetManagerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val grpcServerRule =
    FakeGrpcServer.createFakeGrpcServer("InspectorTargetTest", transportService)
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionRule)!!

  @Test
  fun launchTarget() =
    runBlocking<Unit> {
      appInspectionRule.launchTarget(AppInspectionTestUtils.createFakeProcessDescriptor())
      assertThat(appInspectionRule.targetManager.targets).hasSize(1)
    }

  @Test
  fun disposesClientsForProject() =
    runBlocking<Unit> {
      val terminatedProcess =
        FakeTransportService.FAKE_PROCESS.toBuilder().setName("dispose").setPid(1).build()
      val terminateProcessDescriptor =
        AppInspectionTestUtils.createFakeProcessDescriptor(process = terminatedProcess)

      val otherProcess =
        FakeTransportService.FAKE_PROCESS.toBuilder().setName("normal").setPid(2).build()
      val otherProcessDescriptor =
        AppInspectionTestUtils.createFakeProcessDescriptor(process = otherProcess)

      val target = appInspectionRule.launchTarget(otherProcessDescriptor, TEST_PROJECT)
      val disposeTarget = appInspectionRule.launchTarget(terminateProcessDescriptor, "dispose")
      target.launchInspector(
        AppInspectionTestUtils.createFakeLaunchParameters(
          otherProcessDescriptor,
          project = TEST_PROJECT
        )
      )
      val disposeClient =
        disposeTarget.launchInspector(
          AppInspectionTestUtils.createFakeLaunchParameters(
            terminateProcessDescriptor,
            project = "dispose"
          )
        )

      assertThat(appInspectionRule.targetManager.targets).hasSize(2)

      appInspectionRule.targetManager.disposeClients("dispose")
      disposeClient.awaitForDisposal()

      assertThat(appInspectionRule.targetManager.targets).hasSize(1)
      assertThat(appInspectionRule.targetManager.targets.values.first().targetDeferred.await())
        .isSameAs(target)
    }

  @Test
  fun processTerminationCleansUpTarget() =
    runBlocking<Unit> {
      val process = AppInspectionTestUtils.createFakeProcessDescriptor()
      appInspectionRule.launchTarget(process)

      assertThat(appInspectionRule.targetManager.targets).hasSize(1)

      appInspectionRule.targetManager.onProcessDisconnected(process)

      assertThat(appInspectionRule.targetManager.targets).isEmpty()
    }
}
