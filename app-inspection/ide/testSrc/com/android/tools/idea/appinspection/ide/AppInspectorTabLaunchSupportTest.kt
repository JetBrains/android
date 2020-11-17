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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
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
import com.intellij.openapi.project.Project
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

  private val resolvedJar = AppInspectorJar("resolved")

  private val notApplicableInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID) {
    override fun isApplicable() = false
  }
  private val frameworkInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID) {}
  private val incompatibleLibraryInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_2) {
    override val inspectorLaunchParams = LibraryInspectorLaunchParams(
      TEST_JAR, ArtifactCoordinate("incompatible", "lib", "INCOMPATIBLE", ArtifactCoordinate.Type.JAR))
  }
  private val libraryInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_3) {
    override val inspectorLaunchParams = LibraryInspectorLaunchParams(TEST_JAR,
                                                                      ArtifactCoordinate("a", "b", "1.0.0", ArtifactCoordinate.Type.JAR))
  }

  private val unresolvedLibrary = ArtifactCoordinate("fallback", "library", "1.0.0", ArtifactCoordinate.Type.JAR)
  private val unresolvedJar = AppInspectorJar("fallback_jar")
  private val unresolvedLibraryInspector = object : AppInspectorTabProvider by StubTestAppInspectorTabProvider(INSPECTOR_ID_3) {
    override val inspectorLaunchParams = LibraryInspectorLaunchParams(unresolvedJar, unresolvedLibrary)
  }

  /**
   * This tests all 4 possible scenarios during and leading up to the launch of a library inspector tab + 1 framework inspector.
   *
   * The 4 library inspectors are:
   * 1) not applicable - inspector tab shouldn't be included in result
   * 2) incompatible inspector - an info tab should be created with an appropriate error message
   * 3) compatible inspector - a mutable tab created and inspector jar set to the resolved one
   * 4) compatible inspector but failed to resolve its jar - an info tab should be created with an appropriate error message
   */
  @Test
  fun getApplicableTabProviders() = runBlocking<Unit> {
    val resolver = object : ArtifactResolver {
      override suspend fun resolveArtifacts(artifactIdList: List<ArtifactCoordinate>,
                                            project: Project): Map<ArtifactCoordinate, AppInspectorJar> {
        return artifactIdList.filter { it != unresolvedLibrary }.associateWith { resolvedJar }
      }
    }
    val support = AppInspectorTabLaunchSupport(
      { listOf(notApplicableInspector, frameworkInspector, incompatibleLibraryInspector, libraryInspector, unresolvedLibraryInspector) },
      appInspectionServiceRule.apiServices,
      projectRule.project,
      resolver
    )

    transportService.setCommandHandler(
      Commands.Command.CommandType.APP_INSPECTION,
      TestAppInspectorCommandHandler(timer, getLibraryVersionsResponse = { getLibraryVersionsCommand ->
        AppInspection.GetLibraryCompatibilityInfoResponse.newBuilder().addAllResponses(
          getLibraryVersionsCommand.targetLibrariesList.map { coordinate ->
            if (coordinate.version == "INCOMPATIBLE") {
              AppInspection.LibraryCompatibilityInfo.newBuilder().setStatus(
                AppInspection.LibraryCompatibilityInfo.Status.INCOMPATIBLE)
                .setTargetLibrary(coordinate).setVersion(coordinate.version).build()
            }
            else {
              AppInspection.LibraryCompatibilityInfo.newBuilder().setStatus(
                AppInspection.LibraryCompatibilityInfo.Status.COMPATIBLE)
                .setTargetLibrary(coordinate).setVersion(coordinate.version).build()
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
    val inspectorTabs = tabs.filterIsInstance<LaunchableInspectorTabLaunchParams>()
    val infoTabs = tabs.filterIsInstance<StaticInspectorTabLaunchParams>()

    assertThat(inspectorTabs).hasSize(2)
    assertThat(infoTabs).hasSize(2)

    assertThat(infoTabs.map { it.provider }).containsExactly(incompatibleLibraryInspector, unresolvedLibraryInspector)

    inspectorTabs.forEach { tab ->
      when (tab.provider) {
        frameworkInspector -> {
          assertThat(tab.jar).isSameAs(TEST_JAR)
        }
        else -> {
          assertThat(tab.jar).isSameAs(resolvedJar)
        }
      }
    }
  }
}