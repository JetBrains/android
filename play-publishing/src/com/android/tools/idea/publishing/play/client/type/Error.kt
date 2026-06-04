/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing.play.client.type

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data classes to parse Google API error responses.
 *
 * See: https://cloud.google.com/apis/design/errors#http_mapping
 */
@Serializable data class GoogleApiErrorResponse(val error: GoogleApiError = GoogleApiError())

@Serializable
data class GoogleApiError(
  val code: Int = 0,
  val message: String = "",
  val errors: List<GoogleApiInnerError>? = null,
  val status: String? = null,
)

@Serializable
data class GoogleApiInnerError(
  val message: String? = null,
  val domain: String? = null,
  val reason: String? = null,
  val debugInfo: String? = null,
)

private val jsonParser = Json { ignoreUnknownKeys = true }

/** Helper to parse the error message from a response content String. */
fun parseGoogleApiError(content: String): GoogleApiError? {
  return try {
    jsonParser.decodeFromString<GoogleApiErrorResponse>(content).error
  } catch (_: Exception) {
    null
  }
}
