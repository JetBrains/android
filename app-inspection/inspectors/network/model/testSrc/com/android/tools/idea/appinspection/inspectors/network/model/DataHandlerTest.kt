package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.StubNetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData.Companion.createHttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.JavaThread
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

  @Test
  fun handleHttpConnectionEvent_incrementalUpdates() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val range = Range(9.secondsInMicros, 20.secondsInMicros)
    val id = 1L

    handler.handleHttpConnectionEvent(
      requestStarted(id, 10.secondsInNanos, "url", "method", "field=1", "trace")
    )
    var expected =
      createHttpData(
        id = id,
        updateTimeUs = 10000000,
        requestStartTimeUs = 10000000,
        url = "url",
        method = "method",
        requestFields = "field=1",
        trace = "trace",
      )
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(
      httpThread(id, 11.secondsInNanos, threadId = 1, threadName = "1")
    )
    expected = expected.copy(updateTimeUs = 11000000, threads = listOf(JavaThread(1, "1")))
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(requestPayload(id, 12.secondsInNanos, "request-payload"))
    expected =
      expected.copy(updateTimeUs = 12000000, requestPayload = "request-payload".toByteString())
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(requestCompleted(id, 13.secondsInNanos))
    expected = expected.copy(updateTimeUs = 13000000, requestCompleteTimeUs = 13000000)
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(responseStarted(id, 14.secondsInNanos, "null=HTTP/1.1 200"))
    expected =
      expected.copy(
        updateTimeUs = 14000000,
        responseStartTimeUs = 14000000,
        responseFields = "null=HTTP/1.1 200"
      )
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(responsePayload(id, 15.secondsInNanos, "response-payload"))
    expected =
      expected.copy(updateTimeUs = 15000000, rawResponsePayload = "response-payload".toByteString())
    assertThat(handler.getHttpDataForRange(range)).containsExactly(expected)

    handler.handleHttpConnectionEvent(responseCompleted(id, 16.secondsInNanos))
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
      requestStarted(id1, 10.secondsInNanos),
      requestStarted(id2, 11.secondsInNanos),
      httpClosed(id1, 12.secondsInNanos, true),
      httpClosed(id2, 13.secondsInNanos, true),
    )

    assertThat(handler.getHttpDataForRange(range))
      .containsExactly(
        createHttpData(
          id = 1,
          updateTimeUs = 12000000,
          requestStartTimeUs = 10000000,
          connectionEndTimeUs = 12000000,
        ),
        createHttpData(
          id = 2,
          updateTimeUs = 13000000,
          requestStartTimeUs = 11000000,
          connectionEndTimeUs = 13000000,
        ),
      )
      .inOrder()
  }

  @Test
  fun getDataForRange() {
    val handler = DataHandler(StubNetworkInspectorTracker())
    val id1 = 1L
    val id2 = 2L

    handler.handleHttpConnectionEvent(
      requestStarted(id1, 10.secondsInNanos),
      requestStarted(id2, 11.secondsInNanos),
      httpClosed(id1, 12.secondsInNanos, true),
      httpClosed(id2, 13.secondsInNanos, true),
    )
    val data1 =
      createHttpData(
        id = 1,
        updateTimeUs = 12000000,
        requestStartTimeUs = 10000000,
        connectionEndTimeUs = 12000000,
      )
    val data2 =
      createHttpData(
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
    val httpStart = requestStarted(id, 2.secondsInNanos)
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
    val httpStart1 = requestStarted(id1, 2.secondsInNanos)
    val speed1 = speedEvent(4.secondsInNanos, 10, 10)
    val httpStart2 = requestStarted(id2, 5.secondsInNanos)
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
    val httpStart1 = requestStarted(id1, 2.secondsInNanos)
    val speed1 = speedEvent(4.secondsInNanos, 10, 10)
    val httpStart2 = requestStarted(id2, 5.secondsInNanos)
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
}

private fun DataHandler.getSpeedForRangeSec(range: IntRange) =
  getSpeedForRange(Range(range.first.secondsInMicros, range.last.secondsInMicros))

private fun DataHandler.getHttpDataForRangeSec(range: IntRange) =
  getHttpDataForRange(Range(range.first.secondsInMicros, range.last.secondsInMicros))

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
