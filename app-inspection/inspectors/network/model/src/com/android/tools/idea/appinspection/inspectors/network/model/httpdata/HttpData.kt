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
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.io.URLUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

const val APPLICATION_FORM_MIME_TYPE = "application/x-www-form-urlencoded"

/**
 * Data of http url connection. Each [HttpData] object matches a http connection with a unique id,
 * and it includes both request data and response data. Request data is filled immediately when the
 * connection starts. Response data may start empty but filled when connection completes.
 */
data class HttpData(
  val id: Long,
  val requestStartTimeUs: Long,
  val requestCompleteTimeUs: Long,
  val responseStartTimeUs: Long,
  val responseCompleteTimeUs: Long,
  val connectionEndTimeUs: Long,
  val threads: List<JavaThread>,
  val url: String,
  val method: String,
  val trace: String,
  val requestFields: String,
  val requestPayload: ByteString,
  val responseFields: String,
  private val rawResponsePayload: ByteString
) {

  /**
   * Threads that access a connection. The first thread is the thread that creates the connection.
   */
  val javaThreads = threads
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

  class ContentType(private val contentType: String) {
    val isEmpty = contentType.isEmpty()

    /**
     * @return MIME type related information from Content-Type because Content-Type may contain
     * other information such as charset or boundary.
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
      requestStartTimeUs: Long,
      requestCompleteTimeUs: Long,
      responseStartTimeUs: Long,
      responseCompleteTimeUs: Long,
      connectionEndTimeUs: Long,
      threads: List<JavaThread>,
      url: String = "",
      method: String = "",
      trace: String = "",
      requestFields: String = "",
      requestPayload: ByteString = ByteString.EMPTY,
      responseFields: String = "",
      responsePayload: ByteString = ByteString.EMPTY
    ): HttpData {
      assert(threads.isNotEmpty()) { "HttpData must be initialized with at least one thread" }
      return HttpData(
        id,
        requestStartTimeUs,
        requestCompleteTimeUs,
        responseStartTimeUs,
        responseCompleteTimeUs,
        connectionEndTimeUs,
        threads.distinctBy { it.id },
        url,
        method,
        trace,
        requestFields,
        requestPayload,
        responseFields,
        responsePayload
      )
    }

    /**
     * Return the name of the URL, which is the final complete word in the path portion of the URL.
     * The query is included as it can be useful to disambiguate requests. Additionally, the
     * returned value is URL decoded, so that, say, "Hello%2520World" -> "Hello World".
     *
     * For example, "www.example.com/demo/" -> "demo" "www.example.com/test.png" -> "test.png"
     * "www.example.com/test.png?res=2" -> "test.png?res=2" "www.example.com/" -> "www.example.com"
     */
    fun getUrlName(url: String): String {
      return try {
        // Run encode on the incoming url once, just in case, as this can prevent URI.create from
        // throwing a syntax exception in some cases. This encode will be decoded, below.
        val uri = URI.create(URLEncoder.encode(url, CharsetToolkit.UTF8))

        if (uri.path != null) {
          val lastComponent = lastComponent(uri.path)
          val fullname = uri.query?.let { "$lastComponent?${uri.query}" } ?: lastComponent
          decodeUrlName(fullname)
        } else {
          uri.host
        }
      } catch (ignored: UnsupportedEncodingException) {
        // If here, it most likely means the url we are tracking is invalid in some way (formatting
        // or encoding). We try to recover gracefully by employing a simpler, less sophisticated
        // approach - return all text after the last slash. Keep in mind that this fallback
        // case should rarely, if ever, be used in practice.
        lastComponent(url)
      } catch (ignored: IllegalArgumentException) {
        lastComponent(url)
      }
    }

    private fun lastComponent(url: String) = url.trimEnd('/').substringAfterLast('/')

    private fun decodeUrlName(name: String): String {
      // URL might be encoded an arbitrarily deep number of times. Keep decoding until we peel away
      // the final layer.
      // Usually this is only expected to loop once or twice.
      // See more: http://stackoverflow.com/questions/3617784/plus-signs-being-replaced-for-252520
      var currentName = name
      var lastName: String
      do {
        lastName = currentName
        currentName = URLUtil.decode(currentName)
      } while (currentName != lastName)
      return currentName
    }
  }
}

/**
 * Thread information fetched from the JVM, as opposed to from native code. See also:
 * https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html
 */
data class JavaThread(val id: Long, val name: String)
