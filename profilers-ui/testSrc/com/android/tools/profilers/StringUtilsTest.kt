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
package com.android.tools.profilers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringUtilsTest {

  @Test
  fun `short path left alone`() {
    assertThat(StringUtils.abbreviatePath("/a/b/c")).isEqualTo("/a/b/c")
  }

  @Test
  fun `long path abbreviated`() {
    assertThat(StringUtils.abbreviatePath("/a/b/c/d/e")).isEqualTo("/a/b/.../d/e")
  }

  @Test
  fun `best fitting suffix gives longest suffix for width`() {
    assertThat(StringUtils.bestFittingSuffix("0123456789", 4, String::length)).isEqualTo("6789")
    assertThat(StringUtils.bestFittingSuffix("0123456789", 10, String::length)).isEqualTo("0123456789")
  }
}