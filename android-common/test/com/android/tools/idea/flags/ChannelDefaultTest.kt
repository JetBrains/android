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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ChannelDefaultTest {
  @Test
  fun devOverride() {
    val versionProvider = { "Iguana | 2023.1.1 dev" }

    val defaultValue =
      ChannelDefault.of(1, versionProvider)
        .withDevOverride(2)
        .withNightlyOverride(3)
        .withCanaryOverride(4)
        .withBetaOverride(5)
        .withRCOverride(6)
        .withStableOverride(7)

    assertThat(defaultValue.get()).isEqualTo(2)
  }

  @Test
  fun nightlyOverride() {
    val versionProvider = { "Iguana | 2023.1.1 Nightly" }

    val defaultValue =
      ChannelDefault.of(1, versionProvider)
        .withDevOverride(2)
        .withNightlyOverride(3)
        .withCanaryOverride(4)
        .withBetaOverride(5)
        .withRCOverride(6)
        .withStableOverride(7)

    assertThat(defaultValue.get()).isEqualTo(3)
  }

  @Test
  fun canaryOverride() {
    val versionProvider = { "Iguana | 2023.1.1 Canary" }

    val defaultValue =
      ChannelDefault.of(1, versionProvider)
        .withDevOverride(2)
        .withNightlyOverride(3)
        .withCanaryOverride(4)
        .withBetaOverride(5)
        .withRCOverride(6)
        .withStableOverride(7)

    assertThat(defaultValue.get()).isEqualTo(4)
  }

  @Test
  fun betaOverride() {
    val versionProvider = { "Iguana | 2023.1.1 Beta" }

    val defaultValue =
      ChannelDefault.of(1, versionProvider)
        .withDevOverride(2)
        .withNightlyOverride(3)
        .withCanaryOverride(4)
        .withBetaOverride(5)
        .withRCOverride(6)
        .withStableOverride(7)

    assertThat(defaultValue.get()).isEqualTo(5)
  }

  @Test
  fun rcOverride() {
    val versionProvider = { "Iguana | 2023.1.1 RC" }

    val defaultValue =
      ChannelDefault.of(1, versionProvider)
        .withDevOverride(2)
        .withNightlyOverride(3)
        .withCanaryOverride(4)
        .withBetaOverride(5)
        .withRCOverride(6)
        .withStableOverride(7)

    assertThat(defaultValue.get()).isEqualTo(6)
  }

  @Test
  fun stableOverride() {
    val versionProvider = { "Iguana | 2023.1.1" }

    val defaultValue =
      ChannelDefault.of(1, versionProvider)
        .withDevOverride(2)
        .withNightlyOverride(3)
        .withCanaryOverride(4)
        .withBetaOverride(5)
        .withRCOverride(6)
        .withStableOverride(7)

    assertThat(defaultValue.get()).isEqualTo(7)
  }

  @Test
  fun noMatchingOverride() {
    val versionProvider = { "Iguana | 2023.1.1 Dev" }

    val defaultValue =
      ChannelDefault.of(1, versionProvider)
        .withNightlyOverride(3)
        .withCanaryOverride(4)
        .withBetaOverride(5)
        .withRCOverride(6)
        .withStableOverride(7)

    assertThat(defaultValue.get()).isEqualTo(1)
  }
}
