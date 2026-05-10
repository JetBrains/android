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

import com.google.api.client.http.HttpResponseException
import com.google.api.client.util.Key
import com.google.gson.Gson

/**
 * Data classes to parse Google API error responses.
 *
 * See: https://cloud.google.com/apis/design/errors#http_mapping
 */
data class GoogleApiErrorResponse(@field:Key var error: GoogleApiError = GoogleApiError())

data class GoogleApiError(
  @field:Key var code: Int = 0,
  @field:Key var message: String = "",
  @field:Key var errors: List<GoogleApiInnerError>? = null,
  @field:Key var status: String? = null,
)

data class GoogleApiInnerError(
  @field:Key var message: String? = null,
  @field:Key var domain: String? = null,
  @field:Key var reason: String? = null,
  @field:Key var debugInfo: String? = null,
)

/** Helper to parse the error message from a [HttpResponseException]. */
fun HttpResponseException.parseGoogleApiError(): GoogleApiError? {
  return try {
    Gson().fromJson(content, GoogleApiErrorResponse::class.java)?.error
  } catch (_: Exception) {
    null
  }
}
