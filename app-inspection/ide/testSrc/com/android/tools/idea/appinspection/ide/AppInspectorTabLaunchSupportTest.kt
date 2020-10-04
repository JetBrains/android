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
package com.android.tools.idea.appinspection.ide

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createFakeProcessDescriptor
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.INSPECTOR_ID_2
import com.android.tools.idea.appinspection.test.INSPECTOR_ID_3
import com.android.tools.idea.appinspection.test.TEST_JAR
import com.android.tools.idea.appinspection.test.TestAppInspectorCommandHandler
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain


class AppInspectorTabLaunchSupportTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)
  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionViewTest", transportService, transportService)!!
  private val appInspectionServiceRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)
  private val projectRule = AndroidProjectRule.inMemory().initAndroid(false)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionServiceRule)!!.around(projectRule)!!


  private val frameworkInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID) {}
  private val incompatibleLibraryInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_2) {
    override val inspectorLaunchParams = LibraryInspectorLaunchParams(
      TEST_JAR, ArtifactCoordinate("incompatible", "lib", "INCOMPATIBLE", ArtifactCoordinate.Type.JAR))
  }
  private val libraryInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_3) {
    override val inspectorLaunchParams = LibraryInspectorLaunchParams(TEST_JAR,
                                                                      ArtifactCoordinate("a", "b", "1.0.0", ArtifactCoordinate.Type.JAR))
  }

  /**
   * This tests the call to getApplicableTabProviders with all possible inspector scenarios:
   *
   * Scenarios tested:
   *   1) framework inspector
   *   2) library inspector
   *   3) library inspector that is incompatible
   *
   * Scenarios 1 & 2 should result in LAUNCH status. Scenario 3 should result in INCOMPATIBLE status.
   */
  @Test
  fun getApplicableTabProviders_allScenarios() = runBlocking<Unit> {
    val support = AppInspectorTabLaunchSupport({ listOf(frameworkInspector, incompatibleLibraryInspector, libraryInspector) },
                                               appInspectionServiceRule.apiServices, projectRule.project)

    transportService.setCommandHandler(
      Commands.Command.CommandType.APP_INSPECTION,
      TestAppInspectorCommandHandler(timer, getLibraryVersionsResponse = { getLibraryVersionsCommand ->
        AppInspection.GetLibraryVersionsResponse.newBuilder().addAllResponses(
          getLibraryVersionsCommand.targetVersionsList.map {
            if (it.minVersion == "INCOMPATIBLE") {
              AppInspection.LibraryVersionResponse.newBuilder().setStatus(
                AppInspection.LibraryVersionResponse.Status.INCOMPATIBLE)
                .setVersionFileName(it.versionFileName).setVersion(it.minVersion).build()
            }
            else {
              AppInspection.LibraryVersionResponse.newBuilder().setStatus(
                AppInspection.LibraryVersionResponse.Status.COMPATIBLE)
                .setVersionFileName(it.versionFileName).setVersion(it.minVersion).build()
            }
          }.toList()).build()
      }))

    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    val processReadyDeferred = CompletableDeferred<Unit>()

    appInspectionServiceRule.addProcessListener(object : ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        processReadyDeferred.complete(Unit)
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      }
    })

    processReadyDeferred.await()

    val tabs = support.getApplicableTabLaunchParams(createFakeProcessDescriptor())
    val inspectorTabs = tabs.filter { it.status == AppInspectorTabLaunchParams.Status.LAUNCH }
    val infoTabs = tabs.filter { it.status == AppInspectorTabLaunchParams.Status.INCOMPATIBLE }

    assertThat(inspectorTabs).hasSize(2)
    assertThat(infoTabs).hasSize(1)

    assertThat(infoTabs[0].provider).isSameAs(incompatibleLibraryInspector)
  }
}