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
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Test

class HttpDataTest {
  @Test
  fun responseFieldsStringIsCorrectlySplitAndTrimmed() {
    val data =
      createFakeHttpData(
        1,
        responseHeaders =
          listOf(
            httpHeader("status line", "HTTP/1.1 302 Found"),
            httpHeader("first", "1"),
            httpHeader("second", "2"),
            httpHeader("equation", "x+y=10"),
          ),
      )

    val header = data.responseHeaders
    assertThat(header["first"]).containsExactly("1")
    assertThat(header["second"]).containsExactly("2")
    assertThat(header["equation"]).containsExactly("x+y=10")
  }

  @Test
  fun testResponseCode() {
    val data =
      createFakeHttpData(
        1,
        responseHeaders =
          listOf(
            httpHeader("Content-Type", "text/html; charset=UTF-8"),
          ),
        responseCode = 302,
      )
    assertThat(data.responseCode).isEqualTo(302)
    assertThat(data.responseHeaders["content-type"]).containsExactly("text/html; charset=UTF-8")
  }

  @Test
  fun emptyResponseFields() {
    val data = createFakeHttpData(1, responseCode = 200, responseHeaders = emptyList())
    assertThat(data.responseCode).isEqualTo(200)
  }

  @Test
  fun responseFieldsWithDuplicateKey() {
    val data =
      createFakeHttpData(
        1,
        responseHeaders =
          listOf(
            httpHeader("status line", "HTTP/1.1 302 Found"),
            httpHeader("first", "1"),
            httpHeader("second", "2", "5"),
            httpHeader("equation", "x+y=10"),
          ),
      )

    val header = data.responseHeaders
    assertThat(header["first"]).containsExactly("1")
    assertThat(header["second"]).containsExactly("2", "5").inOrder()
    assertThat(header["equation"]).containsExactly("x+y=10")
  }

  @Test
  fun testSetRequestFields() {
    val data =
      createFakeHttpData(
        1,
        requestHeaders =
          listOf(
            httpHeader("first", "1"),
            httpHeader("second", "2"),
            httpHeader("equation", "x+y=10"),
          ),
      )
    val headers = data.requestHeaders
    assertThat(headers.size).isEqualTo(3)
    assertThat(headers["first"]).containsExactly("1")
    assertThat(headers["second"]).containsExactly("2")
    assertThat(headers["equation"]).containsExactly("x+y=10")
  }

  @Test
  fun mimeTypeFromContentType() {
    assertThat(HttpData.ContentType("text/html; charset=utf-8").mimeType).isEqualTo("text/html")
    assertThat(HttpData.ContentType("text/html").mimeType).isEqualTo("text/html")
    assertThat(HttpData.ContentType("text/html;").mimeType).isEqualTo("text/html")
    assertThat(HttpData.ContentType("").mimeType).isEmpty()
  }

  @Test
  fun isFormDataFromContentType() {
    assertThat(HttpData.ContentType("application/x-www-form-urlencoded; charset=utf-8").isFormData)
      .isTrue()
    assertThat(HttpData.ContentType("application/x-www-form-urlencoded").isFormData).isTrue()
    assertThat(HttpData.ContentType("Application/x-www-form-urlencoded;").isFormData).isTrue()
    assertThat(HttpData.ContentType("").isFormData).isFalse()
    assertThat(HttpData.ContentType("test/x-www-form-urlencoded;").isFormData).isFalse()
    assertThat(HttpData.ContentType("application/json; charset=utf-8").isFormData).isFalse()
  }

  @Test
  fun contentLengthFromLowerCaseData() {
    val data =
      createFakeHttpData(
        1,
        responseHeaders =
          listOf(
            httpHeader("CoNtEnt-LEngtH", "10000"),
            httpHeader("response-status-code", "200"),
          )
      )
    assertThat(data.responseHeaders["content-length"]).containsExactly("10000")
    assertThat(data.responseHeaders["cOnTenT-leNGth"]).containsExactly("10000")
  }

  @Test
  fun statusCodeFromProtoField() {
    val data =
      createFakeHttpData(
        1,
        responseHeaders =
          listOf(
            httpHeader("content-length", "10000"),
            httpHeader("response-status-code", "404"),
          ),
        responseCode = 200,
      )
    assertThat(data.responseCode).isEqualTo(200)
  }

  @Test
  fun decodesGzippedResponsePayload() {
    val byteOutput = ByteArrayOutputStream()
    GZIPOutputStream(byteOutput).use { stream -> stream.write("test".encodeToByteArray()) }
    byteOutput.toByteArray()
    val data =
      createFakeHttpData(
        1,
        responseHeaders =
          listOf(
            httpHeader("content-length", "10000"),
            httpHeader("response-status-code", "200"),
            httpHeader("content-encoding", "gzip"),
          ),
        responsePayload = ByteString.copyFrom(byteOutput.toByteArray())
      )
    assertThat(data.getReadableResponsePayload().toStringUtf8()).isEqualTo("test")
  }

  @Test
  fun decodeMalformedGzipResponsePayload_showRawPayload() {
    val malformedBytes = "Not a gzip".toByteArray()
    val data =
      createFakeHttpData(
        1,
        responseHeaders =
          listOf(
            httpHeader("content-length", "10000"),
            httpHeader("response-status-code", "200"),
            httpHeader("content-encoding", "gzip"),
          ),
        responsePayload = ByteString.copyFrom(malformedBytes)
      )
    assertThat(data.getReadableResponsePayload().toByteArray()).isEqualTo(malformedBytes)
  }

  @Test
  fun keyInMapEntriesIsStoredAsIs() {
    val header = "RESPONSE-status-CoDe"
    val data = createFakeHttpData(1, responseHeaders = listOf(httpHeader(header, "200")))
    assertThat(data.responseHeaders.size).isEqualTo(1)
    assertThat(data.responseHeaders.keys.first()).isEqualTo(header)
    assertThat(data.responseHeaders.keys.first()).isNotEqualTo(header.lowercase())
    assertThat(data.responseHeaders[header.lowercase()]).containsExactly("200")
  }
}
