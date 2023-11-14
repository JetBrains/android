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

import com.android.tools.idea.appinspection.inspectors.network.model.httpHeader
import com.android.tools.idea.protobuf.ByteString
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Header
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport

const val FAKE_RESPONSE_CODE = 302
const val FAKE_RESPONSE_DESCRIPTION = "Found"
const val FAKE_CONTENT_TYPE = "image/jpeg"
val FAKE_THREAD = JavaThread(0, "Main Thread")
val FAKE_THREAD_LIST = listOf(FAKE_THREAD)
private const val SECOND_us = 1000000L
private val FAKE_REQUEST = ByteString.copyFromUtf8("REQUEST")
private val FAKE_RESPONSE = ByteString.copyFromUtf8("RESPONSE")

fun fakeUrl(id: Long): String {
  return "http://example.com/$id"
}

fun fakeContentSize(id: Long): Int {
  return (id * 100).toInt()
}

fun fakeStackTrace(id: Long): String {
  return """
            com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)
            com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:$id)
            """
    .trimIndent()
}

fun fakeResponseHeaders(id: Long, contentType: String? = FAKE_CONTENT_TYPE): List<Header> {
  return listOf(
    httpHeader("status line", "HTTP/1.1 $FAKE_RESPONSE_CODE $FAKE_RESPONSE_DESCRIPTION"),
    httpHeader("Content-Type", contentType.toString()),
    httpHeader("connId", id.toString()),
    httpHeader("Content-Length", "${fakeContentSize(id)}"),
  )
}

fun createFakeHttpData(
  id: Long,
  requestStartTimeUs: Long = SECOND_us,
  requestCompleteTimeUs: Long = SECOND_us,
  responseStartTimeUs: Long = SECOND_us,
  responseCompleteTimeUs: Long = SECOND_us,
  connectionEndTimeUs: Long = SECOND_us,
  threads: List<JavaThread> = FAKE_THREAD_LIST,
  url: String = fakeUrl(id),
  method: String = "",
  transport: HttpTransport = HttpTransport.UNDEFINED,
  trace: String = fakeStackTrace(id),
  requestHeaders: List<Header> = emptyList(),
  requestPayload: ByteString = FAKE_REQUEST,
  responseHeaders: List<Header> = fakeResponseHeaders(id),
  responsePayload: ByteString = FAKE_RESPONSE,
  responseCode: Int = FAKE_RESPONSE_CODE,
) =
  HttpData.createHttpData(
    id,
    connectionEndTimeUs,
    requestStartTimeUs,
    requestCompleteTimeUs,
    responseStartTimeUs,
    responseCompleteTimeUs,
    connectionEndTimeUs,
    threads,
    url,
    method,
    transport,
    trace,
    requestHeaders,
    requestPayload,
    responseHeaders,
    responsePayload,
    responseCode,
  )
