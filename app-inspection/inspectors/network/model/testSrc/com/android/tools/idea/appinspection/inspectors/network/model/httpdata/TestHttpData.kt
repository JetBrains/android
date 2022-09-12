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

import com.android.tools.idea.protobuf.ByteString


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
            """.trimIndent()

}

fun fakeResponseFields(id: Long, contentType: String? = FAKE_CONTENT_TYPE): String {
  return "status line = HTTP/1.1 $FAKE_RESPONSE_CODE $FAKE_RESPONSE_DESCRIPTION" +
         "\nContent-Type = $contentType;\nconnId = $id\n Content-Length = ${fakeContentSize(id)}\n"
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
  trace: String = fakeStackTrace(id),
  requestFields: String = "",
  requestPayload: ByteString = FAKE_REQUEST,
  responseFields: String = fakeResponseFields(id),
  responsePayload: ByteString = FAKE_RESPONSE
) = HttpData.createHttpData(
  id, requestStartTimeUs, requestCompleteTimeUs, responseStartTimeUs, responseCompleteTimeUs, connectionEndTimeUs, threads,
  url, method, trace, requestFields, requestPayload, responseFields, responsePayload
)