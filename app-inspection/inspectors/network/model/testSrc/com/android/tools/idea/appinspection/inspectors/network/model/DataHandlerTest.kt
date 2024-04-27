package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.flags.junit.FlagRule
import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.StubNetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.JavaThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testing.DebugLoggerRule
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.Event

/** Tests for [DataHandler] */
class DataHandlerTest {
  @get:Rule val debugLoggerRule = DebugLoggerRule()
  @get:Rule val flagRule = FlagRule(StudioFlags.NETWORK_INSPECTOR_GRPC, true)

  @Test
  fun handleHttpConnectionEvent_incrementalUpdates() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val range = Range(9.secondsInMicros, 20.secondsInMicros)
    val id = 1L

    handler.handleHttpConnectionEvent(
      httpRequestStarted(
        id,
        10.secondsInNanos,
        "url",
        "method",
        listOf(httpHeader("field", "1")),
        "trace"
      )
    )
    var expected =
      HttpData.createHttpData(
        id = id,
        updateTimeUs = 10000000,
        requestStartTimeUs = 10000000,
        url = "url",
        method = "method",
        requestHeaders = listOf(httpHeader("field", "1")),
        trace = "trace",
      )
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(
      httpThread(id, 11.secondsInNanos, threadId = 1, threadName = "1")
    )
    expected = expected.copy(updateTimeUs = 11000000, threads = listOf(JavaThread(1, "1")))
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(httpRequestPayload(id, 12.secondsInNanos, "request-payload"))
    expected =
      expected.copy(updateTimeUs = 12000000, requestPayload = "request-payload".toByteString())
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(httpRequestCompleted(id, 13.secondsInNanos))
    expected = expected.copy(updateTimeUs = 13000000, requestCompleteTimeUs = 13000000)
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(
      httpResponseStarted(id, 14.secondsInNanos, 200, listOf(httpHeader("null", "HTTP/1.1 200")))
    )
    expected =
      expected.copy(
        updateTimeUs = 14000000,
        responseStartTimeUs = 14000000,
        responseHeaders = mapOf("null" to listOf("HTTP/1.1 200")),
        responseCode = 200,
      )
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(
      httpResponsePayload(id, 15.secondsInNanos, "response-payload")
    )
    expected =
      expected.copy(updateTimeUs = 15000000, responsePayload = "response-payload".toByteString())
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(httpResponseCompleted(id, 16.secondsInNanos))
    expected = expected.copy(updateTimeUs = 16000000, responseCompleteTimeUs = 16000000)
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(httpClosed(id, 17.secondsInNanos, true))
    expected = expected.copy(updateTimeUs = 17000000, connectionEndTimeUs = 17000000)
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)
  }

  @Test
  fun handleHttpConnectionEvent_concurrentRequests() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val range = Range(9.secondsInMicros, 20.secondsInMicros)
    val id1 = 1L
    val id2 = 2L

    handler.handleHttpConnectionEvent(
      httpRequestStarted(id1, 10.secondsInNanos),
      httpRequestStarted(id2, 11.secondsInNanos),
      httpClosed(id1, 12.secondsInNanos, true),
      httpClosed(id2, 13.secondsInNanos, true),
    )

    assertThat(handler.getHttpDataForRange(range))
      .containsExactly(
        HttpData.createHttpData(
          id = 1,
          updateTimeUs = 12000000,
          requestStartTimeUs = 10000000,
          connectionEndTimeUs = 12000000,
        ),
        HttpData.createHttpData(
          id = 2,
          updateTimeUs = 13000000,
          requestStartTimeUs = 11000000,
          connectionEndTimeUs = 13000000,
        ),
      )
      .inOrder()
  }

  @Test
  fun getHttpDataForRange() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val id1 = 1L
    val id2 = 2L

    handler.handleHttpConnectionEvent(
      httpRequestStarted(id1, 10.secondsInNanos),
      httpRequestStarted(id2, 11.secondsInNanos),
      httpClosed(id1, 12.secondsInNanos, true),
      httpClosed(id2, 13.secondsInNanos, true),
    )
    val data1 =
      HttpData.createHttpData(
        id = 1,
        updateTimeUs = 12000000,
        requestStartTimeUs = 10000000,
        connectionEndTimeUs = 12000000,
      )
    val data2 =
      HttpData.createHttpData(
        id = 2,
        updateTimeUs = 13000000,
        requestStartTimeUs = 11000000,
        connectionEndTimeUs = 13000000,
      )

    assertThat(handler.getHttpDataForRangeSec(8..9)).isEmpty()
    assertThat(handler.getHttpDataForRangeSec(8..10)).containsExactly(data1)
    assertThat(handler.getHttpDataForRangeSec(8..11)).containsExactly(data1, data2).inOrder()
    assertThat(handler.getHttpDataForRangeSec(12..12)).containsExactly(data1, data2).inOrder()
    assertThat(handler.getHttpDataForRangeSec(13..14)).containsExactly(data2)
  }

  @Test
  fun handleSpeedEvent_addSpeedData() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val event = speedEvent(1.secondsInNanos, 10, 20)

    handler.handleSpeedEvent(event)

    assertThat(handler.getSpeedForRangeSec(0..2)).containsExactly(event)
  }

  @Test
  fun handleSpeedEvent_updateTimeline_firstEvent() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val event = speedEvent(1.secondsInNanos, 10, 20)

    val result = handler.handleSpeedEvent(event)

    assertThat(result.updateTimeline).isFalse()
  }

  /**
   * Test a typical single-connection scenario
   * * Zero or more out-of-band non-zero speed-events
   * * Exactly one zero speed-event
   * * HTTP Connection starts
   * * Zero or more speed-events
   * * HTTP Connection ends
   * * Zero or more non-zero speed-events
   * * Exactly one zero speed-event
   * * Zero or more out-of-band speed-events
   *
   * We expect the results from `handleSpeedEvent` to have `updateTimeline == true` only for
   * non-out-band speed-events.
   */
  @Test
  fun handleSpeedEvent_updateTimeline_singleConnection() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val id = 1L
    val speed1 = speedEvent(1.secondsInNanos, 0, 0)
    val httpStart = httpRequestStarted(id, 2.secondsInNanos)
    val speed2 = speedEvent(3.secondsInNanos, 0, 0)
    val speed3 = speedEvent(4.secondsInNanos, 10, 10)
    val speed4 = speedEvent(5.secondsInNanos, 0, 0)
    val httpEnd = httpClosed(id, 10.secondsInNanos, true)
    val speed5 = speedEvent(11.secondsInNanos, 10, 10)
    val speed6 = speedEvent(12.secondsInNanos, 0, 0)
    val speed7 = speedEvent(13.secondsInNanos, 0, 0)
    val speed8 = speedEvent(14.secondsInNanos, 10, 10)

    assertThat(handler.handleSpeedEvent(speed1).updateTimeline).isFalse()
    assertThat(handler.handleHttpConnectionEvent(httpStart).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed2).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed3).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed4).updateTimeline).isTrue()
    assertThat(handler.handleHttpConnectionEvent(httpEnd).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed5).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed6).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed7).updateTimeline).isFalse()
    assertThat(handler.handleSpeedEvent(speed8).updateTimeline).isFalse()
  }

  /**
   * Test a nested multi-connection scenario
   * * HTTP Connection 1 starts
   * * HTTP Connection 2 starts
   * * HTTP Connection 2 ends
   * * HTTP Connection 1 ends
   */
  @Test
  fun handleSpeedEvent_updateTimeline_nestedMultiConnection() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val id1 = 1L
    val id2 = 2L
    val httpStart1 = httpRequestStarted(id1, 2.secondsInNanos)
    val speed1 = speedEvent(4.secondsInNanos, 10, 10)
    val httpStart2 = httpRequestStarted(id2, 5.secondsInNanos)
    val speed2 = speedEvent(7.secondsInNanos, 10, 10)
    val httpEnd2 = httpClosed(id2, 10.secondsInNanos, true)
    val speed3 = speedEvent(12.secondsInNanos, 0, 0)
    val httpEnd1 = httpClosed(id1, 20.secondsInNanos, true)
    val speed4 = speedEvent(21.secondsInNanos, 10, 10)
    val speed5 = speedEvent(22.secondsInNanos, 0, 0)
    val speed6 = speedEvent(24.secondsInNanos, 10, 10)

    assertThat(handler.handleHttpConnectionEvent(httpStart1).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed1).updateTimeline).isTrue()
    assertThat(handler.handleHttpConnectionEvent(httpStart2).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed2).updateTimeline).isTrue()
    assertThat(handler.handleHttpConnectionEvent(httpEnd2).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed3).updateTimeline).isTrue()
    assertThat(handler.handleHttpConnectionEvent(httpEnd1).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed4).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed5).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed6).updateTimeline).isFalse()
  }

  /**
   * Test an overlapping multi-connection scenario
   * * HTTP Connection 1 starts
   * * HTTP Connection 2 starts
   * * HTTP Connection 1 ends
   * * HTTP Connection 2 ends
   */
  @Test
  fun handleSpeedEvent_updateTimeline_overlappingMultiConnection() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val id1 = 1L
    val id2 = 2L
    val httpStart1 = httpRequestStarted(id1, 2.secondsInNanos)
    val speed1 = speedEvent(4.secondsInNanos, 10, 10)
    val httpStart2 = httpRequestStarted(id2, 5.secondsInNanos)
    val speed2 = speedEvent(7.secondsInNanos, 10, 10)
    val httpEnd1 = httpClosed(id1, 10.secondsInNanos, true)
    val speed3 = speedEvent(12.secondsInNanos, 0, 0)
    val httpEnd2 = httpClosed(id2, 20.secondsInNanos, true)
    val speed4 = speedEvent(21.secondsInNanos, 10, 10)
    val speed5 = speedEvent(22.secondsInNanos, 0, 0)
    val speed6 = speedEvent(24.secondsInNanos, 10, 10)

    assertThat(handler.handleHttpConnectionEvent(httpStart1).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed1).updateTimeline).isTrue()
    assertThat(handler.handleHttpConnectionEvent(httpStart2).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed2).updateTimeline).isTrue()
    assertThat(handler.handleHttpConnectionEvent(httpEnd1).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed3).updateTimeline).isTrue()
    assertThat(handler.handleHttpConnectionEvent(httpEnd2).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed4).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed5).updateTimeline).isTrue()
    assertThat(handler.handleSpeedEvent(speed6).updateTimeline).isFalse()
  }

  @Test
  fun handleGrpcEvent_incrementalUpdates() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val range = Range(9.secondsInMicros, 20.secondsInMicros)
    val id = 1L

    handler.handleGrpcEvent(
      grpcCallStarted(
        id,
        10.secondsInNanos,
        "service",
        "method",
        listOf(grpcMetadata("request-field-1", "1")),
        "trace"
      )
    )
    var expected: GrpcData =
      GrpcData.createGrpcData(
        id = id,
        updateTimeUs = 10000000,
        requestStartTimeUs = 10000000,
        service = "service",
        method = "method",
        requestHeaders = listOf(grpcMetadata("request-field-1", "1")),
        trace = "trace",
      )
    assertThat(handler.getGrpcDataForRange(range)).containsExactly(expected)

    handler.handleGrpcEvent(grpcThread(id, 11.secondsInNanos, threadId = 1, threadName = "1"))
    expected = expected.copy(updateTimeUs = 11000000, threads = listOf(JavaThread(1, "1")))
    assertThat(handler.getGrpcDataForRange(range)).containsExactly(expected)

    handler.handleGrpcEvent(
      grpcMessageSent(
        id,
        12.secondsInNanos,
        "request-bytes".toByteString(),
        "request-type",
        "request-text"
      )
    )
    expected =
      expected.copy(
        updateTimeUs = 12000000,
        requestCompleteTimeUs = 12000000,
        requestPayload = "request-bytes".toByteString(),
        requestType = "request-type",
        requestPayloadText = "request-text"
      )
    assertThat(handler.getGrpcDataForRange(range)).containsExactly(expected)

    handler.handleGrpcEvent(
      grpcStreamCreated(
        id,
        13.secondsInNanos,
        "address",
        listOf(grpcMetadata("request-field-2", "2"))
      )
    )
    expected =
      expected.copy(
        updateTimeUs = 13000000,
        responseStartTimeUs = 13000000,
        address = "address",
        requestHeaders = mapOf("request-field-1" to listOf("1"), "request-field-2" to listOf("2"))
      )
    assertThat(handler.getGrpcDataForRange(range)).containsExactly(expected)

    handler.handleGrpcEvent(
      grpcResponseHeaders(id, 14.secondsInNanos, listOf(grpcMetadata("response-field", "1")))
    )
    expected =
      expected.copy(
        updateTimeUs = 14000000,
        responseHeaders = mapOf("response-field" to listOf("1")),
      )
    assertThat(handler.getGrpcDataForRange(range)).containsExactly(expected)

    handler.handleGrpcEvent(
      grpcMessageReceived(
        id,
        14.secondsInNanos,
        "response-bytes".toByteString(),
        "response-type",
        "response-text"
      )
    )
    expected =
      expected.copy(
        updateTimeUs = 14000000,
        responseCompleteTimeUs = 14000000,
        responsePayload = "response-bytes".toByteString(),
        responseType = "response-type",
        responsePayloadText = "response-text"
      )
    assertThat(handler.getGrpcDataForRange(range)).containsExactly(expected)

    handler.handleGrpcEvent(
      grpcCallEnded(
        id,
        15.secondsInNanos,
        "status",
        "error",
        listOf(grpcMetadata("trailer-field", "foo"))
      )
    )
    expected =
      expected.copy(
        updateTimeUs = 15000000,
        connectionEndTimeUs = 15000000,
        status = "status",
        error = "error",
        responseTrailers = mapOf("trailer-field" to listOf("foo")),
      )
    assertThat(handler.getGrpcDataForRange(range)).containsExactly(expected)
  }

  @Test
  fun handleGrpcEvent_concurrentRequests() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val range = Range(9.secondsInMicros, 20.secondsInMicros)
    val id1 = 1L
    val id2 = 2L

    handler.handleGrpcEvent(
      grpcCallStarted(id1, 10.secondsInNanos),
      grpcCallStarted(id2, 11.secondsInNanos),
      grpcCallEnded(id1, 12.secondsInNanos),
      grpcCallEnded(id2, 13.secondsInNanos),
    )

    assertThat(handler.getGrpcDataForRange(range))
      .containsExactly(
        GrpcData.createGrpcData(
          id = 1,
          updateTimeUs = 12000000,
          requestStartTimeUs = 10000000,
          connectionEndTimeUs = 12000000,
        ),
        GrpcData.createGrpcData(
          id = 2,
          updateTimeUs = 13000000,
          requestStartTimeUs = 11000000,
          connectionEndTimeUs = 13000000,
        ),
      )
      .inOrder()
  }

  @Test
  fun handleGrpcEvent_flagDisabled() {
    StudioFlags.NETWORK_INSPECTOR_GRPC.override(false)
    val handler = DataHandler(StubNetworkInspectorTracker())

    handler.handleGrpcEvent(grpcCallStarted(1, 10.secondsInNanos))

    assertThat(handler.getGrpcDataForRangeSec(0..100)).isEmpty()
  }

  @Test
  fun getGrpcDataForRange() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val id1 = 1L
    val id2 = 2L

    handler.handleGrpcEvent(
      grpcCallStarted(id1, 10.secondsInNanos),
      grpcCallStarted(id2, 11.secondsInNanos),
      grpcCallEnded(id1, 12.secondsInNanos),
      grpcCallEnded(id2, 13.secondsInNanos),
    )
    val data1 =
      GrpcData.createGrpcData(
        id = 1,
        updateTimeUs = 12000000,
        requestStartTimeUs = 10000000,
        connectionEndTimeUs = 12000000,
      )
    val data2 =
      GrpcData.createGrpcData(
        id = 2,
        updateTimeUs = 13000000,
        requestStartTimeUs = 11000000,
        connectionEndTimeUs = 13000000,
      )

    assertThat(handler.getGrpcDataForRangeSec(8..9)).isEmpty()
    assertThat(handler.getGrpcDataForRangeSec(8..10)).containsExactly(data1)
    assertThat(handler.getGrpcDataForRangeSec(8..11)).containsExactly(data1, data2).inOrder()
    assertThat(handler.getGrpcDataForRangeSec(12..12)).containsExactly(data1, data2).inOrder()
    assertThat(handler.getGrpcDataForRangeSec(13..14)).containsExactly(data2)
  }
}

private fun DataHandler.getSpeedForRangeSec(range: IntRange) =
  getSpeedForRange(Range(range.first.secondsInMicros, range.last.secondsInMicros))

private fun DataHandler.getHttpDataForRangeSec(range: IntRange) =
  getHttpDataForRange(Range(range.first.secondsInMicros, range.last.secondsInMicros))

private fun DataHandler.getGrpcDataForRangeSec(range: IntRange) =
  getGrpcDataForRange(Range(range.first.secondsInMicros, range.last.secondsInMicros))

private inline val Int.secondsInNanos
  get() = seconds.inWholeNanoseconds

private inline val Int.secondsInMicros
  get() = seconds.toDouble(DurationUnit.MICROSECONDS)

private fun String.toByteString() = ByteString.copyFromUtf8(this)

private fun DataHandler.handleHttpConnectionEvent(vararg events: Event) =
  events.forEach {
    val result = handleHttpConnectionEvent(it)
    assertThat(result.updateTimeline).isTrue()
  }

private fun DataHandler.handleGrpcEvent(vararg events: Event) =
  events.forEach {
    val result = handleGrpcEvent(it)
    assertThat(result.updateTimeline).isTrue()
  }
