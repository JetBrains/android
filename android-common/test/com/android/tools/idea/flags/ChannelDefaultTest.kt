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
package com.android.tools.idea.flags

import com.android.tools.idea.IdeChannel
import com.android.tools.idea.IdeChannel.Channel.CANARY
import com.android.tools.idea.IdeChannel.Channel.DEV
import com.android.tools.idea.IdeChannel.Channel.NIGHTLY
import com.android.tools.idea.IdeChannel.Channel.RC
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ChannelDefaultTest {
  private lateinit var versionProvider: () -> String
  private fun createChannelDefault() = ChannelDefault.of(1, versionProvider)
    .withOverride(2, DEV, NIGHTLY)
    .withOverride(3, CANARY..RC)

  private fun computeDefaultValue() = createChannelDefault().get()

  @Test
  fun notOverridden() {
    versionProvider = { "Iguana | 2023.1.1" }
    assertThat(computeDefaultValue()).isEqualTo(1)
  }

  @Test
  fun noMatch() {
    versionProvider = { "Iguana | 2023.1.1 Fhqwhgads" }
    assertThat(computeDefaultValue()).isEqualTo(1)
  }

  @Test
  fun overrideFirstParam() {
    versionProvider = { "Iguana | 2023.1.1 dev" }
    assertThat(computeDefaultValue()).isEqualTo(2)
  }

  @Test
  fun overrideVarargParam() {
    versionProvider = { "Iguana | 2023.1.1 Nightly" }
    assertThat(computeDefaultValue()).isEqualTo(2)
  }

  @Test
  fun overrideRange() {
    for (channel in listOf("Canary", "Beta", "RC")) {
      versionProvider = { "Iguana | 2023.1.1 $channel" }
      assertThat(computeDefaultValue()).isEqualTo(3)
    }
  }
}
