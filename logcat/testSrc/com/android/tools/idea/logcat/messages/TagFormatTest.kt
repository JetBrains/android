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
package com.android.tools.idea.logcat.messages

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Test

/**
 * Tests for [TagFormat]
 *
 * Full test coverage is provided by [MessageFormatterTest] where it's formatted within the context of the full log line.
 */
class TagFormatTest {
  @Test
  fun smallMaxLength() {
    assertThat(TagFormat(maxLength = 5).format("tag", previousTag = null)).isEqualTo("tag        ")
  }

  @Test
  fun serialize() {
    val json = Gson().toJson(TagFormat())

    assertThat(Gson().fromJson(json, JsonObject::class.java).keySet()).containsExactly("maxLength", "hideDuplicates", "enabled")
  }
}