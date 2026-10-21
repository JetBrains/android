/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.commands

import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingDeque
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LeakCanaryAnalysisCommandHandlerTest {

  /** Verifies that the handler only accepts the SEND_LEAKCANARY_ANALYSIS command and correctly rejects all other command types. */
  @Test
  fun testShouldHandle() {
    val stub = mock(TransportServiceGrpc.TransportServiceBlockingStub::class.java)
    val queue = LinkedBlockingDeque<Common.Event>()
    val handler = LeakCanaryAnalysisCommandHandler(stub, queue)

    // Should return true for SEND_LEAKCANARY_ANALYSIS
    val handleCommand = Commands.Command.newBuilder().setType(Commands.Command.CommandType.SEND_LEAKCANARY_ANALYSIS).build()
    assertThat(handler.shouldHandle(handleCommand)).isTrue()

    // Should return false for other commands
    val ignoreCommand = Commands.Command.newBuilder().setType(Commands.Command.CommandType.UNSPECIFIED).build()
    assertThat(handler.shouldHandle(ignoreCommand)).isFalse()
  }

  /**
   * Verifies the execution flow of the handler. Ensures that the command's string payload is properly extracted and converted into a
   * LEAKCANARY_ANALYSIS Common.Event, populated with the correct timestamp, process ID, and group ID, and finally dropped into the event
   * queue for the UI layer to consume.
   */
  @Test
  fun testExecute() {
    val stub = mock(TransportServiceGrpc.TransportServiceBlockingStub::class.java)
    val queue = LinkedBlockingDeque<Common.Event>()
    val handler = LeakCanaryAnalysisCommandHandler(stub, queue)

    // Mock the transport stub to return a known timestamp
    val expectedTimestamp = 123456789L
    val timeResponse = Transport.TimeResponse.newBuilder().setTimestampNs(expectedTimestamp).build()
    `when`(stub.getCurrentTime(any(Transport.TimeRequest::class.java))).thenReturn(timeResponse)

    // Create a fake SEND_LEAKCANARY_ANALYSIS command
    val dataPayload = "fake_shark_analysis_json_or_string"
    val analysisCmd = Commands.SendLeakCanaryAnalysisData.newBuilder().setData(dataPayload).build()
    val command =
      Commands.Command.newBuilder()
        .setType(Commands.Command.CommandType.SEND_LEAKCANARY_ANALYSIS)
        .setPid(42)
        .setSendLeakcanaryAnalysis(analysisCmd)
        .build()

    // Execute the handler
    val response = handler.execute(command)

    // Verify it returns the default ExecuteResponse
    assertThat(response).isEqualTo(Transport.ExecuteResponse.getDefaultInstance())

    // Verify the event was successfully pushed into the queue
    assertThat(queue).hasSize(1)
    val event = queue.take()

    // Verify all properties of the generated Common.Event are correct
    assertThat(event.groupId).isEqualTo(42L)
    assertThat(event.pid).isEqualTo(42)
    assertThat(event.kind).isEqualTo(Common.Event.Kind.LEAKCANARY_ANALYSIS)
    assertThat(event.timestamp).isEqualTo(expectedTimestamp)

    // Verify the payload
    assertThat(event.hasLeakcanaryAnalysis()).isTrue()
    assertThat(event.leakcanaryAnalysis.data).isEqualTo(dataPayload)
  }
}
