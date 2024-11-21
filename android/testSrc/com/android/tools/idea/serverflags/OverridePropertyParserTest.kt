/*
 * Copyright (C) 2024 The Android Open Source Project
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

class OverridePropertyParserTest {

  @Test
  fun `verify legacy format`() {
    val parser = OverridePropertyParserImpl(supportMultiValueFlags = false)
    assertThat(parser.parseProperty("")).isEmpty()
    assertThat(parser.parseProperty("analytics/myFlag")).isEqualTo(mapOf("analytics/myFlag" to 0))
    assertThat(parser.parseProperty("analytics/myFlag,experiments/flag2"))
      .isEqualTo(mapOf("analytics/myFlag" to 0, "experiments/flag2" to 0))
  }

  @Test
  fun `verify multi value format`() {
    val parser = OverridePropertyParserImpl(supportMultiValueFlags = true)
    assertThat(parser.parseProperty("")).isEmpty()
    assertThat(parser.parseProperty("analytics/myFlag/0")).isEqualTo(mapOf("analytics/myFlag" to 0))
    assertThat(parser.parseProperty("analytics/myFlag/0,analytics/myFlag2/1"))
      .isEqualTo(mapOf("analytics/myFlag" to 0, "analytics/myFlag2" to 1))
  }

  @Test
  fun `parser falls back to single value format when it cannot parse param`() {
    val parser = OverridePropertyParserImpl(supportMultiValueFlags = true)
    assertThat(parser.parseProperty("analytics/myFlag")).isEqualTo(mapOf("analytics/myFlag" to 0))
    assertThat(parser.parseProperty("analytics/myFlag/1,analytics/myFlag2"))
      .isEqualTo(mapOf("analytics/myFlag" to 1, "analytics/myFlag2" to 0))
  }
}
