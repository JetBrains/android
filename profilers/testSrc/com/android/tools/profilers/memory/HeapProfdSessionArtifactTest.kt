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
package com.android.tools.profilers.memory

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionArtifact
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class HeapProfdSessionArtifactTest {

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  var grpcChannel = FakeGrpcChannel(
    "HeapProfdSessionArtifactTestChannel",
    transportService,
    FakeProfilerService(timer),
    FakeMemoryService(),
    FakeCpuService(),
    FakeEventService()
  )

  private lateinit var profilers: StudioProfilers

  @Before
  fun setup() {
    profilers = StudioProfilers(
      ProfilerClient(grpcChannel.channel),
      FakeIdeProfilerServices(),
      FakeTimer()
    )
  }

  fun generateSessionArtifacts() : List<SessionArtifact<*>> {
    val nativeHeapTimestamp = 30L
    val nativeHeapInfo = Trace.TraceData.newBuilder().setTraceStarted(Trace.TraceData.TraceStarted.newBuilder().setTraceInfo(
      Trace.TraceInfo.newBuilder().setFromTimestamp(nativeHeapTimestamp).setToTimestamp(
        nativeHeapTimestamp + 1))).build()
    val nativeHeapData = ProfilersTestData.generateMemoryTraceData(
      nativeHeapTimestamp, nativeHeapTimestamp + 1, nativeHeapInfo)
      .setPid(ProfilersTestData.SESSION_DATA.pid).build()
    transportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId, nativeHeapData)
    return HeapProfdSessionArtifact.getSessionArtifacts(profilers, ProfilersTestData.SESSION_DATA,
                                                                 Common.SessionMetaData.getDefaultInstance())
  }

  @Test
  fun testGetSessionArtifacts() {
    val artifacts = generateSessionArtifacts()
    assertThat(artifacts).hasSize(1)
    assertThat(artifacts[0].name).isEqualTo("Native Sampled")
    assertThat(artifacts[0].isOngoing).isFalse()
    assertThat(artifacts[0].session).isEqualTo(ProfilersTestData.SESSION_DATA)
  }

  @Test
  fun testExportAppendsSymbols() {
    val artifact = generateSessionArtifacts()[0] as HeapProfdSessionArtifact
    val stream = ByteArrayOutputStream()
    val contents = ByteString.copyFromUtf8("TestData")
    transportService.addFile(artifact.startTime.toString(), contents)
    val symbolData = ByteString.copyFromUtf8("SymbolData");
    val symbolsFile = File("${FileUtil.getTempDirectory()}${File.separator}${artifact.startTime}.symbols")
    symbolsFile.deleteOnExit()
    val outputStream = FileOutputStream(symbolsFile)
    outputStream.write(symbolData.toByteArray())
    outputStream.close()
    artifact.export(stream)
    val output = contents.concat(symbolData)
    assertThat(stream.toByteArray()).isEqualTo(output.toByteArray())
  }

  @Test
  fun testExportWithoutSymbols() {
    val artifact = generateSessionArtifacts()[0] as HeapProfdSessionArtifact
    val stream = ByteArrayOutputStream()
    val contents = ByteString.copyFromUtf8("TestData")
    transportService.addFile(artifact.startTime.toString(), contents)
    artifact.export(stream)
    assertThat(stream.toByteArray()).isEqualTo(contents.toByteArray())
  }
}