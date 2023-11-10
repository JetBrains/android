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
package com.android.tools.idea.appinspection.inspectors.network.model.httpdata

import com.android.tools.adtui.model.Range
import com.android.tools.idea.protobuf.ByteString
import com.intellij.util.io.URLUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport

const val APPLICATION_FORM_MIME_TYPE = "application/x-www-form-urlencoded"

/**
 * Data of http url connection. Each [HttpData] object matches a http connection with a unique id,
 * and it includes both request data and response data. Request data is filled immediately when the
 * connection starts. Response data may start empty but filled when connection completes.
 */
data class HttpData(
  val id: Long,
  val updateTimeUs: Long,
  val requestStartTimeUs: Long,
  val requestCompleteTimeUs: Long,
  val responseStartTimeUs: Long,
  val responseCompleteTimeUs: Long,
  val connectionEndTimeUs: Long,
  val threads: List<JavaThread>,
  val url: String,
  val method: String,
  val transport: HttpTransport,
  val trace: String,
  val requestFields: String,
  val requestPayload: ByteString,
  val responseFields: String,
  private val rawResponsePayload: ByteString,
) {

  val requestHeader = RequestHeader(requestFields)
  val responseHeader = ResponseHeader(responseFields)

  // The unzipped version of the response payload. Note not all response payloads are zipped,
  // so this could be the same as the rawResponsePayload.
  private lateinit var unzippedResponsePayload: ByteString
  val responsePayload: ByteString
    get() {
      if (this::unzippedResponsePayload.isInitialized) {
        return unzippedResponsePayload
      } else {
        if (responseHeader.getField("content-encoding").lowercase().contains("gzip")) {
          try {
            GZIPInputStream(ByteArrayInputStream(rawResponsePayload.toByteArray())).use {
              inputStream ->
              unzippedResponsePayload = ByteString.copyFrom(inputStream.readBytes())
            }
          } catch (ignored: IOException) {
            // If we got here, it means we failed to unzip data that was supposedly zipped. Just
            // fallback and return the content directly.
            unzippedResponsePayload = rawResponsePayload
          }
        } else {
          unzippedResponsePayload = rawResponsePayload
        }
        return unzippedResponsePayload
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
      transport = requestStarted.transport,
      trace = requestStarted.trace,
      requestFields = requestStarted.fields,
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
    return copy(
      updateTimeUs = timestamp,
      requestCompleteTimeUs = timestamp,
    )
  }

  internal fun withResponseStarted(event: NetworkInspectorProtocol.Event): HttpData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      updateTimeUs = timestamp,
      responseStartTimeUs = timestamp,
      responseFields = event.httpConnectionEvent.httpResponseStarted.fields,
    )
  }

  internal fun withResponsePayload(event: NetworkInspectorProtocol.Event) =
    copy(
      updateTimeUs = TimeUnit.NANOSECONDS.toMicros(event.timestamp),
      rawResponsePayload = event.httpConnectionEvent.responsePayload.payload,
    )

  internal fun withResponseCompleted(event: NetworkInspectorProtocol.Event): HttpData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      updateTimeUs = timestamp,
      responseCompleteTimeUs = timestamp,
    )
  }

  internal fun withHttpClosed(event: NetworkInspectorProtocol.Event): HttpData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      updateTimeUs = timestamp,
      connectionEndTimeUs = timestamp,
    )
  }

  internal fun intersectsRange(range: Range): Boolean {
    val start = requestStartTimeUs
    val end = updateTimeUs
    val min = range.min.toLong()
    val max = range.max.toLong()
    return start <= max && end >= min
  }

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
      requestFields: String = "",
      requestPayload: ByteString = ByteString.EMPTY,
      responseFields: String = "",
      responsePayload: ByteString = ByteString.EMPTY,
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
        requestFields,
        requestPayload,
        responseFields,
        responsePayload,
      )
    }
  }
}

/**
 * Return the name of the URL, which is the final complete word in the path portion of the URL. The
 * query is included as it can be useful to disambiguate requests. Additionally, the returned value
 * is URL decoded, so that, say, "Hello%2520World" -> "Hello World".
 *
 * For example, "www.example.com/demo/" -> "demo" "www.example.com/test.png" -> "test.png"
 * "www.example.com/test.png?res=2" -> "test.png?res=2" "www.example.com/" -> "www.example.com"
 */
fun HttpData.getUrlName(): String {
  val name =
    try {
      val uri = URI.create(url)
      val path = uri.path?.lastComponent()
      when {
        path == null -> uri.host
        uri.query != null -> "$path?${uri.query}"
        path.isBlank() -> uri.host
        else -> path
      }
    } catch (ignored: IllegalArgumentException) {
      url.lastComponent()
    }
  return name.decodeUrl()
}

fun HttpData.getUrlPath() = runCatching { URI.create(url)?.path }.getOrNull() ?: "Unknown"

fun HttpData.getUrlHost() = runCatching { URI.create(url)?.host }.getOrNull() ?: "Unknown"

fun HttpData.getUrlScheme() = runCatching { URI.create(url)?.scheme }.getOrNull() ?: "Unknown"

fun HttpData.getMimeType() = responseHeader.contentType.mimeType.split("/").last()

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

/**
 * Thread information fetched from the JVM, as opposed to from native code. See also:
 * https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html
 */
data class JavaThread(val id: Long, val name: String)

private fun NetworkInspectorProtocol.Event.toJavaThread() =
  JavaThread(httpConnectionEvent.httpThread.threadId, httpConnectionEvent.httpThread.threadName)
