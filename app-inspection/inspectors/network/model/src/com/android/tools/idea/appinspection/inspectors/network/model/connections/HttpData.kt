/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model.connections

import com.android.tools.adtui.model.Range
import com.android.tools.idea.protobuf.ByteString
import com.intellij.util.io.URLUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Header
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport

const val APPLICATION_FORM_MIME_TYPE = "application/x-www-form-urlencoded"
private const val CONTENT_ENCODING = "content-encoding"
private const val CONTENT_TYPE = "content-type"

/**
 * Data of http url connection. Each [HttpData] object matches a http connection with a unique id,
 * and it includes both request data and response data. Request data is filled immediately when the
 * connection starts. Response data may start empty but filled when connection completes.
 */
data class HttpData(
  override val id: Long,
  override val updateTimeUs: Long,
  override val requestStartTimeUs: Long,
  override val requestCompleteTimeUs: Long,
  override val responseStartTimeUs: Long,
  override val responseCompleteTimeUs: Long,
  override val connectionEndTimeUs: Long,
  override val threads: List<JavaThread>,
  override val url: String,
  override val method: String,
  val httpTransport: HttpTransport,
  override val trace: String,
  override val requestHeaders: Map<String, List<String>>,
  override val requestPayload: ByteString,
  override val responseHeaders: Map<String, List<String>>,
  override val responsePayload: ByteString,
  val responseCode: Int,
) : ConnectionData {
  private val uri: URI? = runCatching { URI.create(url) }.getOrNull()

  override val transport: String
    get() = httpTransport.toDisplayText()

  override val schema: String
    get() = uri?.scheme ?: "Unknown"

  override val address: String
    get() = uri?.host ?: "Unknown"

  override val path: String
    get() = uri?.path ?: "Unknown"

  /**
   * Return the name of the URL, which is the final complete word in the path portion of the URL.
   * The query is included as it can be useful to disambiguate requests. Additionally, the returned
   * value is URL decoded, so that, say, "Hello%2520World" -> "Hello World".
   *
   * For example, "www.example.com/demo/" -> "demo" "www.example.com/test.png" -> "test.png"
   * "www.example.com/test.png?res=2" -> "test.png?res=2" "www.example.com/" -> "www.example.com"
   */
  override val name: String =
    if (uri == null) {
      url.lastComponent()
    } else {
      val path = uri.path?.lastComponent()
      when {
        path == null -> uri.host
        uri.query != null -> "$path?${uri.query}"
        path.isBlank() -> uri.host ?: "Unknown"
        else -> path
      }.decodeUrl()
    }

  override val requestType: String
    get() = getRequestContentType().mimeType

  override val requestPayloadText: String
    get() = "N/A"

  override val status: String
    get() = if (responseCode < 0) "" else responseCode.toString()

  override val error: String
    get() = "N/A"

  override val responseType: String
    get() = getResponseContentType().mimeType

  override val responsePayloadText: String
    get() = "N/A"

  override val responseTrailers: Map<String, List<String>>
    get() = emptyMap()

  fun getReadableResponsePayload(): ByteString {
    return if (getContentEncodings().find { it.lowercase() == "gzip" } != null) {
      try {
        GZIPInputStream(ByteArrayInputStream(responsePayload.toByteArray())).use {
          ByteString.copyFrom(it.readBytes())
        }
      } catch (ignored: IOException) {
        responsePayload
      }
    } else {
      responsePayload
    }
  }

  internal fun withRequestStarted(event: NetworkInspectorProtocol.Event): HttpData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    val requestStarted = event.httpConnectionEvent.httpRequestStarted
    return copy(
      updateTimeUs = timestamp,
      requestStartTimeUs = timestamp,
      url = requestStarted.url,
      method = requestStarted.method,
      httpTransport = requestStarted.transport,
      trace = requestStarted.trace,
      requestHeaders = requestStarted.headersList.toMap(),
    )
  }

  internal fun withHttpThread(event: NetworkInspectorProtocol.Event) =
    copy(
      updateTimeUs = TimeUnit.NANOSECONDS.toMicros(event.timestamp),
      threads = threads + event.toJavaThread(),
    )

  internal fun withRequestPayload(event: NetworkInspectorProtocol.Event) =
    copy(
      updateTimeUs = TimeUnit.NANOSECONDS.toMicros(event.timestamp),
      requestPayload = event.httpConnectionEvent.requestPayload.payload,
    )

  internal fun withRequestCompleted(event: NetworkInspectorProtocol.Event): HttpData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(updateTimeUs = timestamp, requestCompleteTimeUs = timestamp)
  }

  internal fun withResponseStarted(event: NetworkInspectorProtocol.Event): HttpData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    val responseStarted = event.httpConnectionEvent.httpResponseStarted
    return copy(
      updateTimeUs = timestamp,
      responseStartTimeUs = timestamp,
      responseCode = responseStarted.responseCode,
      responseHeaders = responseStarted.headersList.toMap(),
    )
  }

  internal fun withResponsePayload(event: NetworkInspectorProtocol.Event) =
    copy(
      updateTimeUs = TimeUnit.NANOSECONDS.toMicros(event.timestamp),
      responsePayload = event.httpConnectionEvent.responsePayload.payload,
    )

  internal fun withResponseCompleted(event: NetworkInspectorProtocol.Event): HttpData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(updateTimeUs = timestamp, responseCompleteTimeUs = timestamp)
  }

  internal fun withHttpClosed(event: NetworkInspectorProtocol.Event): HttpData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(updateTimeUs = timestamp, connectionEndTimeUs = timestamp)
  }

  internal fun intersectsRange(range: Range): Boolean {
    val start = requestStartTimeUs
    val end = updateTimeUs
    val min = range.min.toLong()
    val max = range.max.toLong()
    return start <= max && end >= min
  }

  fun getRequestContentType() = ContentType(requestHeaders[CONTENT_TYPE]?.firstOrNull() ?: "")

  fun getResponseContentType() = ContentType(responseHeaders[CONTENT_TYPE]?.firstOrNull() ?: "")

  class ContentType(private val contentType: String) {
    val isEmpty = contentType.isEmpty()

    /**
     * @return MIME type related information from Content-Type because Content-Type may contain
     *   other information such as charset or boundary.
     *
     * Examples: "text/html; charset=utf-8" => "text/html" "text/html" => "text/html"
     */
    val mimeType = contentType.split(';').first()

    override fun toString() = contentType

    val isFormData = mimeType.equals(APPLICATION_FORM_MIME_TYPE, ignoreCase = true)
  }

  companion object {
    fun createHttpData(
      id: Long,
      updateTimeUs: Long = 0,
      requestStartTimeUs: Long = 0,
      requestCompleteTimeUs: Long = 0,
      responseStartTimeUs: Long = 0,
      responseCompleteTimeUs: Long = 0,
      connectionEndTimeUs: Long = 0,
      threads: List<JavaThread> = emptyList(),
      url: String = "",
      method: String = "",
      transport: HttpTransport = HttpTransport.UNDEFINED,
      trace: String = "",
      requestHeaders: List<Header> = emptyList(),
      requestPayload: ByteString = ByteString.EMPTY,
      responseHeaders: List<Header> = emptyList(),
      responsePayload: ByteString = ByteString.EMPTY,
      responseCode: Int = -1,
    ): HttpData {
      return HttpData(
        id,
        updateTimeUs,
        requestStartTimeUs,
        requestCompleteTimeUs,
        responseStartTimeUs,
        responseCompleteTimeUs,
        connectionEndTimeUs,
        threads.distinctBy { it.id },
        url,
        method,
        transport,
        trace,
        requestHeaders.toMap(),
        requestPayload,
        responseHeaders.toMap(),
        responsePayload,
        responseCode,
      )
    }
  }
}

fun HttpData.getContentEncodings() = responseHeaders[CONTENT_ENCODING] ?: emptyList()

private fun String.lastComponent() = trimEnd('/').substringAfterLast('/')

/**
 * Decodes a URL Component
 *
 * A URL might be encoded an arbitrarily deep number of times. Keep decoding until we peel away the
 * final layer. Usually this is only expected to loop once or twice.
 * [See more](http://stackoverflow.com/questions/3617784/plus-signs-being-replaced-for-252520)
 */
private fun String.decodeUrl(): String {
  var currentValue = this
  var lastValue: String
  do {
    lastValue = currentValue
    try {
      currentValue = URLUtil.decode(currentValue)
    } catch (e: Exception) {
      return this
    }
  } while (currentValue != lastValue)
  return currentValue
}

private fun NetworkInspectorProtocol.Event.toJavaThread() =
  JavaThread(httpConnectionEvent.httpThread.threadId, httpConnectionEvent.httpThread.threadName)

private fun List<Header>.toMap() =
  associateTo(TreeMap(String.CASE_INSENSITIVE_ORDER)) { it.key to it.valuesList }

private fun HttpTransport.toDisplayText() =
  when (this) {
    HttpTransport.JAVA_NET -> "Java Native"
    HttpTransport.OKHTTP2 -> "OkHttp 2"
    HttpTransport.OKHTTP3 -> "OkHttp 3"
    HttpTransport.UNDEFINED,
    HttpTransport.UNRECOGNIZED -> "Unknown"
  }
