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
package com.android.tools.idea.appinspection.test

import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.wireless.android.sdk.stats.AppInspectionEvent

const val INSPECTOR_ID = "test.inspector.1"
const val INSPECTOR_ID_2 = "test.inspector.2"
const val INSPECTOR_ID_3 = "test.inspector.3"

val TEST_JAR = AppInspectorJar("test")

const val TEST_PROJECT = "test.project"

const val MIN_VERSION = "0.0.0-dev"
val TEST_ARTIFACT = ArtifactCoordinate("test_group_id", "test_artifact_id", MIN_VERSION, ArtifactCoordinate.Type.JAR)

/**
 * A collection of utility functions for inspection tests.
 */
object AppInspectionTestUtils {

  /**
   * Creates an [AppInspectionEvent] with the provided [data] and inspector [name].
   */
  fun createRawAppInspectionEvent(
    data: ByteArray,
    name: String = INSPECTOR_ID
  ): AppInspection.AppInspectionEvent = AppInspection.AppInspectionEvent.newBuilder()
    .setInspectorId(name)
    .setRawEvent(
      AppInspection.RawEvent.newBuilder()
        .setContent(ByteString.copyFrom(data))
        .build()
    )
    .build()

  fun createFakeProcessDescriptor(
    device: Common.Device = FakeTransportService.FAKE_DEVICE,
    process: Common.Process = FakeTransportService.FAKE_PROCESS
  ): ProcessDescriptor {
    val stream = Common.Stream.newBuilder().setDevice(device).setStreamId(device.deviceId).build()
    return TransportProcessDescriptor(stream, process)
  }

  fun createFakeLaunchParameters(
    descriptor: ProcessDescriptor = createFakeProcessDescriptor(),
    inspectorId: String = INSPECTOR_ID,
    jar: AppInspectorJar = TEST_JAR,
    project: String = TEST_PROJECT
  ) = LaunchParameters(descriptor, inspectorId, jar, project, TEST_ARTIFACT)

  /**
   * Keeps track of the copied jar so tests could verify the operation happened.
   */
  object TestTransportJarCopier : AppInspectionJarCopier {
    private const val deviceBasePath = "/test/"
    lateinit var copiedJar: AppInspectorJar

    override fun copyFileToDevice(jar: AppInspectorJar): List<String> {
      copiedJar = jar
      return listOf(deviceBasePath + jar.name)
    }
  }
}