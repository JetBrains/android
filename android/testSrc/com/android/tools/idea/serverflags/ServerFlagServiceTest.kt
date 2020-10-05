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

import com.android.tools.idea.ServerFlag
import com.android.tools.idea.ServerFlagTest
import com.google.common.truth.Truth.assertThat
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
    protoValue = ServerFlagTest.newBuilder().apply {
      content = "content"
    }.build().toByteString()
  }.build()
)

private val TEST_PROTO = ServerFlagTest.newBuilder().apply {
  content = "default"
}.build()

private const val CONFIGURATION_VERSION = 123456L

class ServerFlagServiceTest {
  @Test
  fun testRetrieval() {
    val service = ServerFlagServiceImpl(CONFIGURATION_VERSION, FLAGS)

    checkRetrieval(service, "boolean", ServerFlagService::getBoolean, true)
    checkRetrieval(service, "int", ServerFlagService::getInt, 1)
    checkRetrieval(service, "float", ServerFlagService::getFloat, 1f)
    checkRetrieval(service, "string", ServerFlagService::getString, "foo")
    checkProto(service, "proto", "content")
  }

  @Test
  fun testDefaults() {
    val service = ServerFlagServiceImpl(CONFIGURATION_VERSION, FLAGS)
    checkDefault(service, ServerFlagService::getBoolean, false)
    checkDefault(service, ServerFlagService::getInt, 10)
    checkDefault(service, ServerFlagService::getFloat, 10f)
    checkDefault(service, ServerFlagService::getString, "bar")
    checkProto(service, "missing", "default")
  }

  @Test
  fun testNulls() {
    val service = ServerFlagServiceImpl(CONFIGURATION_VERSION, FLAGS)
    checkNull(service, ServerFlagService::getBoolean)
    checkNull(service, ServerFlagService::getInt)
    checkNull(service, ServerFlagService::getFloat)
    checkNull(service, ServerFlagService::getString)
    checkProto(service, "missing", "default")
  }

  @Test
  fun testExceptions() {
    val service = ServerFlagServiceImpl(CONFIGURATION_VERSION, FLAGS)
    checkException(service, "boolean", ServerFlagService::getInt)
    checkException(service, "int", ServerFlagService::getFloat)
    checkException(service, "float", ServerFlagService::getString)
    checkException(service, "string") { s, name -> s.getProtoMessage(name, TEST_PROTO) }
    checkException(service, "proto", ServerFlagService::getBoolean)
  }

  @Test
  fun testEmptyService() {
    val service = ServerFlagService.instance
    assertThat(service.initialized).isFalse()
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
    val service = ServerFlagServiceImpl(CONFIGURATION_VERSION, FLAGS)
    assertThat(service.initialized).isTrue()
    assertThat(CONFIGURATION_VERSION).isEqualTo(service.configurationVersion)
    assertThat(listOf("boolean", "int", "float", "string", "proto")).containsExactlyElementsIn(service.names)
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
    assertThat((service.getProtoMessage(name, TEST_PROTO) as ServerFlagTest).content).isEqualTo(expected)
  }

  private fun <T> checkException(service: ServerFlagService, name: String, retrieve: (ServerFlagService, String) -> T?) {
    var exception: IllegalArgumentException? = null
    try {
      retrieve(service, name)
    }
    catch(e: Exception) {
      exception = e as? IllegalArgumentException
    }
    assertThat(exception).isNotNull()
  }
}