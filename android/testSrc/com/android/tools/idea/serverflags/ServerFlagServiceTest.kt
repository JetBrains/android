/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.serverflags

import com.android.tools.idea.serverflags.protos.ServerFlag
import com.android.tools.idea.serverflags.protos.ServerFlagTest
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.protobuf.ByteString
import org.junit.Before
import org.junit.Test

private val FLAGS = mapOf(
  "boolean" to ServerFlag.newBuilder().apply {
    percentEnabled = 0
    booleanValue = true
  }.build(),
  "int" to ServerFlag.newBuilder().apply {
    percentEnabled = 25
    intValue = 1
  }.build(),
  "float" to ServerFlag.newBuilder().apply {
    percentEnabled = 50
    floatValue = 1f
  }.build(),
  "string" to ServerFlag.newBuilder().apply {
    percentEnabled = 75
    stringValue = "foo"
  }.build(),
  "proto" to ServerFlag.newBuilder().apply {
    percentEnabled = 100
    protoValue = Any.pack(ServerFlagTest.newBuilder().apply {
      content = "content"
    }.build())
  }.build()
)

private val TEST_PROTO = ServerFlagTest.newBuilder().apply {
  content = "default"
}.build()

private const val CONFIGURATION_VERSION = 123456L

class ServerFlagServiceTest {
  var service: ServerFlagService = ServerFlagServiceEmpty

  @Before
  fun setup() {
    ServerFlagServiceImpl.initializer = { ServerFlagInitializationData(CONFIGURATION_VERSION, FLAGS) }
    service = ServerFlagServiceImpl()
  }

  @Test
  fun testRetrieval() {
    checkRetrieval(service, "boolean", ServerFlagService::getBoolean, true)
    checkRetrieval(service, "int", ServerFlagService::getInt, 1)
    checkRetrieval(service, "float", ServerFlagService::getFloat, 1f)
    checkRetrieval(service, "string", ServerFlagService::getString, "foo")
    checkProto(service, "proto", "content")
  }

  @Test
  fun testDefaults() {
    checkDefault(service, ServerFlagService::getBoolean, false)
    checkDefault(service, ServerFlagService::getInt, 10)
    checkDefault(service, ServerFlagService::getFloat, 10f)
    checkDefault(service, ServerFlagService::getString, "bar")
    checkProto(service, "missing", "default")
  }

  @Test
  fun testNulls() {
    checkNull(service, ServerFlagService::getBoolean)
    checkNull(service, ServerFlagService::getInt)
    checkNull(service, ServerFlagService::getFloat)
    checkNull(service, ServerFlagService::getString)
    checkProtoNull(service, "missing")
  }

  @Test
  fun testIncorrectType() {
    checkIncorrectType(service, "boolean", ServerFlagService::getInt)
    checkIncorrectType(service, "int", ServerFlagService::getFloat)
    checkIncorrectType(service, "float", ServerFlagService::getString)
    checkProtoNull(service, "string")
    checkIncorrectType(service, "proto", ServerFlagService::getBoolean)
  }

  @Test
  fun testInvalidProto() {
    val proto = ServerFlag.newBuilder().apply {
      percentEnabled = 100
      protoValue = Any.newBuilder().apply {
        value = ByteString.copyFromUtf8("some bytes")
      }.build()
    }.build()

    val map = mapOf("proto" to proto)
    ServerFlagServiceImpl.initializer = { ServerFlagInitializationData(CONFIGURATION_VERSION, map) }
    val service = ServerFlagServiceImpl()
    val retrieved = service.getProto("proto", TEST_PROTO)
    assertThat(retrieved).isEqualTo(TEST_PROTO)

    val retrievedOrNull = service.getProtoOrNull("proto", TEST_PROTO)
    assertThat(retrievedOrNull).isNull()
  }

  @Test
  fun testEmptyService() {
    val service = ServerFlagServiceEmpty
    assertThat(service.configurationVersion).isEqualTo(-1)
    assertThat(service.names).isEmpty()

    checkNull(service, ServerFlagService::getBoolean)
    checkNull(service, ServerFlagService::getInt)
    checkNull(service, ServerFlagService::getFloat)
    checkNull(service, ServerFlagService::getString)
    checkProto(service, "missing", "default")
  }

  @Test
  fun testProperties() {
    val default = ServerFlagTest.newBuilder().apply {
      content = "default"
    }.build()

    assertThat((service.getProto("proto", default) as ServerFlagTest).content).isEqualTo("content")
    assertThat((service.getProto("missing", default) as ServerFlagTest).content).isEqualTo("default")
    assertThat(CONFIGURATION_VERSION).isEqualTo(service.configurationVersion)
    assertThat(listOf("boolean", "int", "float", "string", "proto")).containsExactlyElementsIn(service.names)
  }

  @Test
  fun testToString() {
    val expected = """
Name: boolean
PercentEnabled: 0
Value: true

Name: float
PercentEnabled: 50
Value: 1.0

Name: int
PercentEnabled: 25
Value: 1

Name: proto
PercentEnabled: 100
Value: custom proto

Name: string
PercentEnabled: 75
Value: foo


""".trimIndent()
    assertThat(service.toString()).isEqualTo(expected)
  }

  @Test
  fun testToStringEmpty() {
    ServerFlagServiceImpl.initializer = { ServerFlagInitializationData(-1, emptyMap()) }
    val service = ServerFlagServiceImpl()
    assertThat(service.toString()).isEqualTo("No server flags are enabled.")
  }

  private fun <T> checkRetrieval(service: ServerFlagService, name: String, retrieve: (ServerFlagService, String) -> T, expected: T) {
    assertThat(retrieve(service, name)).isEqualTo(expected)
  }

  private fun <T> checkDefault(service: ServerFlagService, retrieve: (ServerFlagService, String, T) -> T, default: T) {
    assertThat(retrieve(service, "missing", default)).isEqualTo(default)
  }

  private fun <T> checkNull(service: ServerFlagService, retrieve: (ServerFlagService, String) -> T?) {
    assertThat(retrieve(service, "missing")).isNull()
  }

  private fun checkProto(service: ServerFlagService, name: String, expected: String) {
    assertThat(service.getProto(name, TEST_PROTO).content).isEqualTo(expected)
  }

  private fun checkProtoNull(service: ServerFlagService, name: String) {
    assertThat(service.getProtoOrNull(name, TEST_PROTO)).isNull()
  }

  private fun <T> checkIncorrectType(service: ServerFlagService, name: String, retrieve: (ServerFlagService, String) -> T?) {
    assertThat(retrieve(service, name)).isNull()
  }
}
