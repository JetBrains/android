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
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.process.SimpleProcessListener
import com.android.tools.idea.appinspection.inspector.api.awaitForDisposal
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.DefaultAppInspectionTarget
import com.android.tools.idea.appinspection.internal.toLibraryCompatibilityInfo
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createArtifactCoordinate
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createFakeLaunchParameters
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createFakeProcessDescriptor
import com.android.tools.idea.appinspection.test.TEST_PROJECT
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AppInspectionTargetTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val gRpcServerRule =
    FakeGrpcServer.createFakeGrpcServer("InspectorTargetTest", transportService)
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, gRpcServerRule)

  @get:Rule val ruleChain = RuleChain.outerRule(gRpcServerRule).around(appInspectionRule)!!

  @Test
  fun attachNewAgent() =
    runBlocking<Unit> {
      transportService.addDevice(FakeTransportService.FAKE_DEVICE)
      transportService.addProcess(
        FakeTransportService.FAKE_DEVICE,
        FakeTransportService.FAKE_PROCESS
      )

      appInspectionRule.launchTarget(createFakeProcessDescriptor())
    }

  @Test
  fun attachExistingAgent() =
    runBlocking<Unit> {
      transportService.addDevice(FakeTransportService.FAKE_DEVICE)
      transportService.addProcess(
        FakeTransportService.FAKE_DEVICE,
        FakeTransportService.FAKE_PROCESS
      )

      transportService.setCommandHandler(
        Commands.Command.CommandType.ATTACH_AGENT,
        object : CommandHandler(timer) {
          override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
            throw RuntimeException(
              "App Inspection shouldn't send an ATTACH_AGENT command. Agent is already connected."
            )
          }
        }
      )

      transportService.addEventToStream(
        FakeTransportService.FAKE_DEVICE_ID,
        Common.Event.newBuilder()
          .setCommandId(123)
          .setPid(FakeTransportService.FAKE_PROCESS.pid)
          .setKind(Common.Event.Kind.AGENT)
          .setAgentData(
            Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build()
          )
          .setTimestamp(timer.currentTimeNs + 1)
          .build()
      )

      appInspectionRule.launchTarget(createFakeProcessDescriptor())
    }

  @Test
  fun launchInspector() =
    runBlocking<Unit> {
      val target = appInspectionRule.launchTarget(createFakeProcessDescriptor())
      target.launchInspector(createFakeLaunchParameters())
    }

  @Test
  fun launchInspectorReturnsCorrectConnection() =
    runBlocking<Unit> {
      val target = appInspectionRule.launchTarget(createFakeProcessDescriptor())

      transportService.setCommandHandler(
        Commands.Command.CommandType.APP_INSPECTION,
        object : CommandHandler(timer) {
          override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
            if (command.appInspectionCommand.inspectorId == "never_connects") {
              // Generate a false "successful" service response with a non-existent commandId, to
              // test connection filtering. That is, we don't
              // want this response to be accepted by any pending connections.
              events.add(
                Common.Event.newBuilder()
                  .setKind(Common.Event.Kind.APP_INSPECTION_RESPONSE)
                  .setPid(command.pid)
                  .setTimestamp(timer.currentTimeNs)
                  .setCommandId(command.commandId)
                  .setIsEnded(true)
                  .setAppInspectionResponse(
                    AppInspection.AppInspectionResponse.newBuilder()
                      .setCommandId(1324562)
                      .setStatus(AppInspection.AppInspectionResponse.Status.SUCCESS)
                      .setCreateInspectorResponse(
                        AppInspection.CreateInspectorResponse.getDefaultInstance()
                      )
                      .build()
                  )
                  .build()
              )
            } else {
              // Manually generate correct response to the following launch command.
              events.add(
                Common.Event.newBuilder()
                  .setKind(Common.Event.Kind.APP_INSPECTION_RESPONSE)
                  .setPid(command.pid)
                  .setTimestamp(timer.currentTimeNs)
                  .setCommandId(command.commandId)
                  .setIsEnded(true)
                  .setAppInspectionResponse(
                    AppInspection.AppInspectionResponse.newBuilder()
                      .setCommandId(command.appInspectionCommand.commandId)
                      .setStatus(AppInspection.AppInspectionResponse.Status.SUCCESS)
                      .setCreateInspectorResponse(
                        AppInspection.CreateInspectorResponse.getDefaultInstance()
                      )
                      .build()
                  )
                  .build()
              )
            }
          }
        }
      )
      // Launch an inspector connection that will never be established (assuming the test passes).
      val unsuccessfulJob = launch {
        target.launchInspector(createFakeLaunchParameters(inspectorId = "never_connects"))
      }

      try {
        // Launch an inspector connection that will be successfully established.
        val successfulJob = launch {
          target.launchInspector(createFakeLaunchParameters(inspectorId = "connects_successfully"))
        }

        successfulJob.join()
        assertThat(unsuccessfulJob.isActive).isTrue()
      } finally {
        unsuccessfulJob.cancelAndJoin()
      }
    }

  @Test
  fun processTerminationDisposesClient() =
    runBlocking<Unit> {
      val target =
        appInspectionRule.launchTarget(createFakeProcessDescriptor()) as DefaultAppInspectionTarget

      // Launch an inspector client.
      val client = target.launchInspector(createFakeLaunchParameters())

      // Fake target termination to dispose of client.
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

      client.awaitForDisposal()

      // Launch the same inspector client again.
      val client2 = target.launchInspector(createFakeLaunchParameters())

      // Verify the cached inspector client was removed when it was disposed. The 2nd client is a
      // brand new client.
      assertThat(client).isNotSameAs(client2)
    }

  @Test
  fun disposeTargetCancelsAllInspectors() =
    runBlocking<Unit> {
      val target =
        appInspectionRule.launchTarget(createFakeProcessDescriptor()) as DefaultAppInspectionTarget

      val clientLaunchParams1 = createFakeLaunchParameters(inspectorId = "a")
      val clientLaunchParams2 = createFakeLaunchParameters(inspectorId = "b")

      val client1 = target.launchInspector(clientLaunchParams1)
      val client2 = target.launchInspector(clientLaunchParams2)

      target.dispose()

      client1.awaitForDisposal()
      client2.awaitForDisposal()
    }

  // Verifies the marshalling and unmarshalling of GetLibraryVersion's params.
  @Test
  fun getLibraryVersions() =
    runBlocking<Unit> {
      val process = createFakeProcessDescriptor()

      // The fake response to be manually sent.
      val fakeLibraryVersionsResponse =
        listOf(
          AppInspection.LibraryCompatibilityInfo.newBuilder()
            .setStatus(AppInspection.LibraryCompatibilityInfo.Status.COMPATIBLE)
            .setTargetLibrary(
              createArtifactCoordinate("1st", "file", "1.0.0").toArtifactCoordinateProto()
            )
            .build(),
          AppInspection.LibraryCompatibilityInfo.newBuilder()
            .setStatus(AppInspection.LibraryCompatibilityInfo.Status.INCOMPATIBLE)
            .setTargetLibrary(
              createArtifactCoordinate("2nd", "file", "1.0.0").toArtifactCoordinateProto()
            )
            .setErrorMessage("incompatible")
            .build(),
          AppInspection.LibraryCompatibilityInfo.newBuilder()
            .setStatus(AppInspection.LibraryCompatibilityInfo.Status.LIBRARY_MISSING)
            .setTargetLibrary(
              createArtifactCoordinate("3rd", "file", "1.0.0").toArtifactCoordinateProto()
            )
            .setErrorMessage("missing")
            .build(),
          AppInspection.LibraryCompatibilityInfo.newBuilder()
            .setStatus(AppInspection.LibraryCompatibilityInfo.Status.APP_PROGUARDED)
            .setTargetLibrary(
              createArtifactCoordinate("4rd", "file", "1.0.0").toArtifactCoordinateProto()
            )
            .setErrorMessage("proguarded")
            .build(),
          AppInspection.LibraryCompatibilityInfo.newBuilder()
            .setStatus(AppInspection.LibraryCompatibilityInfo.Status.VERSION_MISSING)
            .setTargetLibrary(
              createArtifactCoordinate("5th", "file", "1.0.0").toArtifactCoordinateProto()
            )
            .setErrorMessage("missing")
            .build(),
          AppInspection.LibraryCompatibilityInfo.newBuilder()
            .setStatus(AppInspection.LibraryCompatibilityInfo.Status.SERVICE_ERROR)
            .setTargetLibrary(
              createArtifactCoordinate("6th", "file", "1.0.0").toArtifactCoordinateProto()
            )
            .setErrorMessage("error")
            .build()
        )

      transportService.setCommandHandler(
        Commands.Command.CommandType.APP_INSPECTION,
        object : CommandHandler(timer) {
          override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
            if (command.appInspectionCommand.hasGetLibraryCompatibilityInfoCommand()) {
              // Reply with fake response.
              events.add(
                Common.Event.newBuilder()
                  .setKind(Common.Event.Kind.APP_INSPECTION_RESPONSE)
                  .setPid(process.pid)
                  .setTimestamp(timer.currentTimeNs)
                  .setCommandId(command.commandId)
                  .setIsEnded(true)
                  .setAppInspectionResponse(
                    AppInspection.AppInspectionResponse.newBuilder()
                      .setCommandId(command.appInspectionCommand.commandId)
                      .setStatus(AppInspection.AppInspectionResponse.Status.SUCCESS)
                      .setGetLibraryCompatibilityResponse(
                        AppInspection.GetLibraryCompatibilityInfoResponse.newBuilder()
                          .addAllResponses(fakeLibraryVersionsResponse)
                      )
                      .build()
                  )
                  .build()
              )
            }
          }
        }
      )

      // These are the version files we are interested in targeting.
      val targets =
        listOf(
          LibraryCompatibility(
            ArtifactCoordinate("1st", "file", "1.0.0", ArtifactCoordinate.Type.JAR)
          ),
          LibraryCompatibility(
            ArtifactCoordinate("2nd", "file", "1.0.0", ArtifactCoordinate.Type.JAR)
          ),
          LibraryCompatibility(
            ArtifactCoordinate("3rd", "file", "1.0.0", ArtifactCoordinate.Type.JAR)
          ),
          LibraryCompatibility(
            ArtifactCoordinate("4th", "file", "1.0.0", ArtifactCoordinate.Type.JAR)
          ),
          LibraryCompatibility(
            ArtifactCoordinate("5th", "file", "1.0.0", ArtifactCoordinate.Type.JAR),
            listOf("com.example.MyClass")
          ),
          LibraryCompatibility(
            ArtifactCoordinate("6th", "file", "1.0.0", ArtifactCoordinate.Type.JAR)
          ),
        )

      // Add the fake process to transport so we can attach to it via apiServices.attachToProcess
      transportService.addDevice(FakeTransportService.FAKE_DEVICE)
      transportService.addProcess(
        FakeTransportService.FAKE_DEVICE,
        FakeTransportService.FAKE_PROCESS
      )
      val processReadyDeferred = CompletableDeferred<Unit>()
      appInspectionRule.apiServices.processDiscovery.addProcessListener(
        MoreExecutors.directExecutor(),
        object : SimpleProcessListener() {
          override fun onProcessConnected(process: ProcessDescriptor) {
            processReadyDeferred.complete(Unit)
          }

          override fun onProcessDisconnected(process: ProcessDescriptor) {}
        }
      )
      processReadyDeferred.await()

      // Verify response.
      val responses =
        appInspectionRule
          .apiServices
          .attachToProcess(process, TEST_PROJECT)
          .getLibraryVersions(targets)
      assertThat(responses)
        .containsExactlyElementsIn(
          fakeLibraryVersionsResponse.mapIndexed { i, response ->
            response.toLibraryCompatibilityInfo(targets[i].coordinate)
          }
        )
    }
}
