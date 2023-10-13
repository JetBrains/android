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
package com.android.tools.idea.insights.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiUtilTest {

  @Test
  fun formatNumberToPrettyString() {
    assertThat(340L.formatNumberToPrettyString()).isEqualTo("340")
    assertThat(1230599L.formatNumberToPrettyString()).isEqualTo("1,230,599")
    assertThat(0L.formatNumberToPrettyString()).isEqualTo("-")
  }

  @Test
  fun shortenEventIdTest() {
    assertThat("event_123456789".shortenEventId()).isEqualTo("123456789")
    assertThat("event_123456789012345".shortenEventId()).isEqualTo("123456789012345")
    assertThat("event_1234567890123456".shortenEventId()).isEqualTo("123456...123456")
    assertThat("event_12345678901234567890".shortenEventId()).isEqualTo("123456...567890")
  }
}
