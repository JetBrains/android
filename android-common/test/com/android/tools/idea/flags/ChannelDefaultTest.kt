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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertFailsWith

/**
 * An exhaustive test for ChannelDefault, mostly as documentation of the expected semantic
 * as this is used to make behavior changes based on the version of Studio
 */
@RunWith(JUnit4::class)
class ChannelDefaultTest {

  @Test
  fun dev() {
    assertThat(enabledUpTo(IdeChannel.Channel.DEV) { "Iguana | 2023.1.1 dev" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.DEV) { "Iguana | 2023.1.1 nightly" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.DEV) { "Iguana | 2023.1.1 canary" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.DEV) { "Iguana | 2023.1.1 beta" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.DEV) { "Iguana | 2023.1.1 rc" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.DEV) { "Iguana | 2023.1.1" }).isFalse()
  }

  @Test
  fun nightly() {
    assertThat(enabledUpTo(IdeChannel.Channel.NIGHTLY) { "Iguana | 2023.1.1 dev" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.NIGHTLY) { "Iguana | 2023.1.1 nightly" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.NIGHTLY) { "Iguana | 2023.1.1 canary" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.NIGHTLY) { "Iguana | 2023.1.1 beta" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.NIGHTLY) { "Iguana | 2023.1.1 rc" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.NIGHTLY) { "Iguana | 2023.1.1" }).isFalse()
  }

  @Test
  fun canary() {
    assertThat(enabledUpTo(IdeChannel.Channel.CANARY) { "Iguana | 2023.1.1 dev" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.CANARY) { "Iguana | 2023.1.1 nightly" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.CANARY) { "Iguana | 2023.1.1 canary" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.CANARY) { "Iguana | 2023.1.1 beta" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.CANARY) { "Iguana | 2023.1.1 rc" }).isFalse()
    assertThat(enabledUpTo(IdeChannel.Channel.CANARY) { "Iguana | 2023.1.1" }).isFalse()
  }

  @Test
  fun beta() {
    val failure = assertFailsWith<IllegalStateException> {
      enabledUpTo(IdeChannel.Channel.BETA) { throw AssertionError("version should be unused") }
    }
    assertThat(failure.message).isEqualTo("Flags must not be conditional between Beta, RC and Stable")
  }

  @Test
  fun rc() {
    val failure = assertFailsWith<IllegalStateException> {
      enabledUpTo(IdeChannel.Channel.RC) { throw AssertionError("version should be unused") }
    }
    assertThat(failure.message).isEqualTo("Flags must not be conditional between Beta, RC and Stable")
  }

  @Test
  fun stable() {
    assertThat(enabledUpTo(IdeChannel.Channel.STABLE) { "Iguana | 2023.1.1 dev" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.STABLE) { "Iguana | 2023.1.1 nightly" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.STABLE) { "Iguana | 2023.1.1 canary" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.STABLE) { "Iguana | 2023.1.1 beta" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.STABLE) { "Iguana | 2023.1.1 rc" }).isTrue()
    assertThat(enabledUpTo(IdeChannel.Channel.STABLE) { "Iguana | 2023.1.1" }).isTrue()
  }


  private fun enabledUpTo(channel: IdeChannel.Channel, versionProvider: () -> String) = ChannelDefault.enabledUpTo(channel, versionProvider).get()
}
