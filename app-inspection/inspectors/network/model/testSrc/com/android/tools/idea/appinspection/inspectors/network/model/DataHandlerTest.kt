package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.StubNetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData.Companion.createHttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.JavaThread
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.Event

/** Tests for [DataHandler] */
class DataHandlerTest {

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

    assertThat(handler.getHttpDataForRange(Range(8.secondsInMicros, 9.secondsInMicros))).isEmpty()
    assertThat(handler.getHttpDataForRange(Range(8.secondsInMicros, 10.secondsInMicros)))
      .containsExactly(data1)
    assertThat(handler.getHttpDataForRange(Range(8.secondsInMicros, 11.secondsInMicros)))
      .containsExactly(data1, data2)
      .inOrder()
    assertThat(handler.getHttpDataForRange(Range(12.secondsInMicros, 12.secondsInMicros)))
      .containsExactly(data1, data2)
      .inOrder()
    assertThat(handler.getHttpDataForRange(Range(13.secondsInMicros, 14.secondsInMicros)))
      .containsExactly(data2)
  }
}

private inline val Int.secondsInNanos
  get() = seconds.inWholeNanoseconds

private inline val Int.secondsInMicros
  get() = seconds.toDouble(DurationUnit.MICROSECONDS)

private fun String.toByteString() = ByteString.copyFromUtf8(this)

private fun DataHandler.handleHttpConnectionEvent(vararg events: Event) =
  events.forEach { handleHttpConnectionEvent(it) }
