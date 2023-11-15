/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.protobuf.ByteString

/** Data of network connection. */
interface ConnectionData {
  val id: Long
  val updateTimeUs: Long
  val requestStartTimeUs: Long
  val requestCompleteTimeUs: Long
  val responseStartTimeUs: Long
  val responseCompleteTimeUs: Long
  val connectionEndTimeUs: Long
  val threads: List<JavaThread>
  val transport: String
  val address: String
  val url: String
  val schema: String
  val method: String
  val path: String
  val name: String
  val trace: String
  val requestHeaders: Map<String, List<String>>
  val requestPayload: ByteString
  val requestType: String
  val requestPayloadText: String
  val status: String
  val error: String
  val responseHeaders: Map<String, List<String>>
  val responsePayload: ByteString
  val responseType: String
  val responsePayloadText: String
  val responseTrailers: Map<String, List<String>>
}
