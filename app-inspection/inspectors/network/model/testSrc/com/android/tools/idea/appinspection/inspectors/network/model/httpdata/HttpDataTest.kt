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
        responseFields =
          """status line =  HTTP/1.1 302 Found
    first=1
    second  = 2
    equation=x+y=10"""
      )

    val header = data.responseHeader
    assertThat(header.getField("first")).isEqualTo("1")
    assertThat(header.getField("second")).isEqualTo("2")
    assertThat(header.getField("equation")).isEqualTo("x+y=10")
  }

  @Test
  fun testResponseStatusLine() {
    val data =
      createFakeHttpData(
        1,
        responseFields =
          """
    null  =  HTTP/1.1 302 Found
    Content-Type =  text/html; charset=UTF-8;  """
      )
    assertThat(data.responseHeader.statusCode).isEqualTo(302)
    assertThat(data.responseHeader.getField("content-type")).isEqualTo("text/html; charset=UTF-8")
  }

  @Test
  fun testResponseStatusLineWithoutKey() {
    val data =
      createFakeHttpData(
        1,
        responseFields = """  HTTP/1.1 200 Found


    Content-Type =  text/html; charset=UTF-8  """
      )
    assertThat(data.responseHeader.statusCode).isEqualTo(200)
    assertThat(data.responseHeader.getField("content-type")).isEqualTo("text/html; charset=UTF-8")
  }

  @Test
  fun emptyResponseFields() {
    val data = createFakeHttpData(1, responseFields = "")
    assertThat(data.responseHeader.statusCode).isEqualTo(-1)
  }

  @Test
  fun emptyResponseFields2() {
    val data = createFakeHttpData(1, responseFields = "   \n  \n  \n\n   \n  ")
    assertThat(data.responseHeader.statusCode).isEqualTo(-1)
  }

  @Test(expected = AssertionError::class)
  fun invalidResponseFields() {
    createFakeHttpData(1, responseFields = "Invalid response fields")
  }

  @Test
  fun responseFieldsWithDuplicateKey() {
    val data =
      createFakeHttpData(
        1,
        responseFields =
          """status line =  HTTP/1.1 302 Found
    first=1
    second  = 2
    equation=x+y=10
    second =5"""
      )

    val header = data.responseHeader
    assertThat(header.getField("first")).isEqualTo("1")
    assertThat(header.getField("second")).isEqualTo("2;5")
    assertThat(header.getField("equation")).isEqualTo("x+y=10")
  }

  @Test
  fun emptyRequestFields() {
    val data = createFakeHttpData(1, requestFields = "")
    assertThat(data.requestHeader.fields).isEmpty()
  }

  @Test
  fun testSetRequestFields() {
    val data = createFakeHttpData(1, requestFields = "\nfirst=1 \n  second  = 2\n equation=x+y=10")
    val requestFields = data.requestHeader.fields
    assertThat(requestFields.size).isEqualTo(3)
    assertThat(requestFields["first"]).isEqualTo("1")
    assertThat(requestFields["second"]).isEqualTo("2")
    assertThat(requestFields["equation"]).isEqualTo("x+y=10")
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
        responseFields = "CoNtEnt-LEngtH = 10000 \n  response-status-code = 200"
      )
    assertThat(data.responseHeader.getField("content-length")).isEqualTo("10000")
    assertThat(data.responseHeader.getField("cOnTenT-leNGth")).isEqualTo("10000")
  }

  @Test
  fun statusCodeFromFields() {
    val data =
      createFakeHttpData(
        1,
        responseFields = "content-length = 10000 \n  response-status-code = 200"
      )
    assertThat(data.responseHeader.statusCode).isEqualTo(200)
  }

  @Test
  fun decodesGzippedResponsePayload() {
    val byteOutput = ByteArrayOutputStream()
    GZIPOutputStream(byteOutput).use { stream -> stream.write("test".encodeToByteArray()) }
    byteOutput.toByteArray()
    val data =
      createFakeHttpData(
        1,
        responseFields =
          "content-length = 10000 \n  response-status-code = 200 \n content-encoding = gzip",
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
        responseFields =
          "content-length = 10000 \n  response-status-code = 200 \n content-encoding = gzip",
        responsePayload = ByteString.copyFrom(malformedBytes)
      )
    assertThat(data.getReadableResponsePayload().toByteArray()).isEqualTo(malformedBytes)
  }

  @Test
  fun keyInMapEntriesIsStoredAsIs() {
    val header = "RESPONSE-status-CoDe"
    val data = createFakeHttpData(1, responseFields = "$header = 200")
    assertThat(data.responseHeader.fields.keys.size).isEqualTo(1)
    assertThat(data.responseHeader.fields.firstKey()).isEqualTo(header)
    assertThat(data.responseHeader.fields.firstKey()).isNotEqualTo(header.lowercase())
    assertThat(data.responseHeader.fields[header.lowercase()]).isEqualTo("200")
  }
}
