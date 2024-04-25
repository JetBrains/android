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
package com.android.tools.idea.serverflags

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerFlagServiceEmptyTest {
  @Test
  fun testEmptyService() {
    val service = ServerFlagServiceEmpty
    assertThat(service.configurationVersion).isEqualTo(-1)
    assertThat(service.names).isEmpty()
    checkNull(service, ServerFlagService::getBoolean)
    checkNull(service, ServerFlagService::getInt)
    checkNull(service, ServerFlagService::getFloat)
    checkNull(service, ServerFlagService::getString)
    checkProto(service)
  }

  private fun <T> checkNull(
    service: ServerFlagService,
    retrieve: (ServerFlagService, String) -> T?
  ) {
    assertThat(retrieve(service, "missing")).isNull()
  }

  private fun checkProto(service: ServerFlagService) {
    assertThat(service.getProto("missing", TEST_PROTO).content).isEqualTo(TEST_PROTO.content)
  }

  companion object {
    private val TEST_PROTO = ServerFlagTest.newBuilder().apply {
      content = "default"
    }.build()
  }
}
