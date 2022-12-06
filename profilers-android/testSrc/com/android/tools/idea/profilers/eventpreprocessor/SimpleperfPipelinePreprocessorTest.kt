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
package com.android.tools.idea.profilers.eventpreprocessor

import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.cpu.FakeTracePreProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SimpleperfPipelinePreprocessorTest {
  companion object {
    const val TRACE_ID = 1234L
  }

  private val validEvent = Common.Event.newBuilder().setCpuTrace(Cpu.CpuTraceData.newBuilder().setTraceStarted(
    Cpu.CpuTraceData.TraceStarted.newBuilder().setTraceInfo(Cpu.CpuTraceInfo.newBuilder()
                                                              .setTraceId(TRACE_ID)
                                                              .setConfiguration(Trace.TraceConfiguration.newBuilder()
                                                                                  .setSimpleperfOptions(
                                                                                    Trace.SimpleperfOptions.newBuilder().addSymbolDirs(
                                                                                      "/path"))))))
    .build()

  @Test
  fun filterOnStartTraceCaptures() {
    val preprocessor = SimpleperfPipelinePreprocessor(FakeTracePreProcessor())
    val event = Common.Event.newBuilder()
    assertThat(preprocessor.shouldPreprocess(event.build())).isFalse()
    event.cpuTrace = Cpu.CpuTraceData.getDefaultInstance()
    assertThat(preprocessor.shouldPreprocess(event.build())).isFalse()
    event.cpuTraceBuilder.traceStarted = Cpu.CpuTraceData.TraceStarted.getDefaultInstance()
    assertThat(preprocessor.shouldPreprocess(event.build())).isFalse()
    event.cpuTraceBuilder.traceStartedBuilder.traceInfo = Cpu.CpuTraceInfo.getDefaultInstance()
    assertThat(preprocessor.shouldPreprocess(event.build())).isFalse()
    event.cpuTraceBuilder.traceStartedBuilder.traceInfoBuilder.configuration = Trace.TraceConfiguration.getDefaultInstance()
    assertThat(preprocessor.shouldPreprocess(event.build())).isFalse()
    assertThat(preprocessor.shouldPreprocess(event.build())).isFalse()
    event.cpuTraceBuilder.traceStartedBuilder.traceInfoBuilder.configurationBuilder.simpleperfOptions =
      Trace.SimpleperfOptions.getDefaultInstance()
    assertThat(preprocessor.shouldPreprocess(event.build())).isTrue()
    assertThat(preprocessor.shouldPreprocess(validEvent)).isTrue()
  }

  @Test
  fun traceIdIsUsedForDataFilter() {
    val preprocessor = SimpleperfPipelinePreprocessor(FakeTracePreProcessor())
    val request = Transport.BytesRequest.newBuilder()
    assertThat(preprocessor.shouldPreprocess(request.build())).isFalse()
    request.setId(TRACE_ID.toString())
    assertThat(preprocessor.shouldPreprocess(request.build())).isFalse()
    preprocessor.preprocessEvent(validEvent)
    assertThat(preprocessor.shouldPreprocess(request.build())).isTrue()
  }

  @Test
  fun traceIsPreprocessed() {
    val fakePreprocessor = FakeTracePreProcessor()
    val pipelinePreprocessor = SimpleperfPipelinePreprocessor(fakePreprocessor)
    val data = ByteString.copyFromUtf8("DATA")
    pipelinePreprocessor.preprocessEvent(validEvent)
    pipelinePreprocessor.preprocessBytes(TRACE_ID.toString(), data)
    assertThat(fakePreprocessor.isTracePreProcessed).isTrue()
    val bytesRequest = Transport.BytesRequest.newBuilder().setId(TRACE_ID.toString()).build()
    assertThat(pipelinePreprocessor.shouldPreprocess(bytesRequest)).isFalse()
  }

}