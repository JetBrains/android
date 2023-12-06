/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_CONNECT
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_DELETE
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_GET
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_HEAD
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_OPTIONS
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_PATCH
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_POST
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_PUT
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_TRACE
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_UNSPECIFIED
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Protocol.PROTOCOL_HTTP
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria.Protocol.PROTOCOL_HTTPS

enum class Protocol(private val displayString: String, val proto: InterceptCriteria.Protocol) {
  HTTPS("https", PROTOCOL_HTTPS),
  HTTP("http", PROTOCOL_HTTP);

  override fun toString() = displayString
}

enum class Method(val proto: InterceptCriteria.Method) {
  ANY(METHOD_UNSPECIFIED),
  GET(METHOD_GET),
  POST(METHOD_POST),
  PUT(METHOD_PUT),
  DELETE(METHOD_DELETE),
  PATCH(METHOD_PATCH),
  HEAD(METHOD_HEAD),
  TRACE(METHOD_TRACE),
  CONNECT(METHOD_CONNECT),
  OPTIONS(METHOD_OPTIONS),
}
