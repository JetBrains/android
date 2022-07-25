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

import org.jetbrains.annotations.VisibleForTesting
import java.util.Locale

private const val FIELD_CONTENT_TYPE = "content-type"
const val FIELD_CONTENT_LENGTH = "content-length"
private const val STATUS_CODE_NAME = "response-status-code"
const val NO_STATUS_CODE = -1

/**
 * A data class for capturing an HTTP header string.
 *
 * It parses the string into a form that is useful for displaying to the user.
 */
sealed class HttpHeader {
  abstract val fields: Map<String, String>

  /**
   * This function does not care about the case of the key.
   * For example: Content-Type and content-type will return the same mapped value.
   */
  @VisibleForTesting
  fun getField(key: String): String {
    return fields.getOrDefault(key.lowercase(Locale.getDefault()), "")
  }

  val contentType: HttpData.ContentType
    get() = HttpData.ContentType(getField(FIELD_CONTENT_TYPE))

  /**
   * @return value of "content-length" in the headers, or -1 if the headers does not contain it.
   */
  val contentLength: Int
    get() {
      val contentLength = getField(FIELD_CONTENT_LENGTH)
      return if (contentLength.isEmpty()) -1 else contentLength.toInt()
    }
}

private fun parseHeaderFields(fields: String): Map<String, String> {
  return fields.split('\n')
    .filter { line: String -> line.trim { it <= ' ' }.isNotEmpty() }
    .map { line ->
      val keyAndValue = line.split('=', limit = 2)
      assert(keyAndValue.size == 2) { "Unexpected http header field ($line)" }
      keyAndValue[0].trim { it <= ' ' }.lowercase(Locale.getDefault()) to keyAndValue[1].trim { it <= ' ' }.trimEnd(';')
    }
    .groupingBy { it.first }
    .aggregate { _, accumulator: String?, element, first ->
      if (first) {
        element.second
      }
      else {
        "$accumulator;${element.second}"
      }
    }
}

class ResponseHeader(@VisibleForTesting val fieldsString: String) : HttpHeader() {
  override val fields = mutableMapOf<String, String>()
  var statusCode = NO_STATUS_CODE


  init {
    val trimmedFields = fieldsString.trim { it <= ' ' }
    if (trimmedFields.isNotEmpty()) {
      // The status-line - should be formatted as per
      // section 6.1 of RFC 2616.
      // https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html
      //
      // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase
      var processedFields = trimmedFields
      val firstLineSplit = trimmedFields.split('\n', limit = 2)
      var status = firstLineSplit[0].trim { it <= ' ' }
      if (status.isNotEmpty()) {
        val tokens = status.split('=', limit = 2)
        status = tokens[tokens.size - 1].trim { it <= ' ' }
        if (status.startsWith("HTTP/1.")) {
          statusCode = status.split(' ')[1].toInt()
          processedFields = if (firstLineSplit.size > 1) firstLineSplit[1] else ""
        }
      }
      val fieldsMap = parseHeaderFields(processedFields).filter { entry ->
        if (entry.key == STATUS_CODE_NAME) {
          this.statusCode = entry.value.toInt()
          false
        }
        true
      }
      assert(statusCode != -1) { "Unexpected http response ($trimmedFields)" }
      fields.putAll(fieldsMap)
    }
  }
}

class RequestHeader(@VisibleForTesting val rawFields: String) : HttpHeader() {
  override val fields = parseHeaderFields(rawFields)
}