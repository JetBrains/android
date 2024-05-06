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
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Supplier

/**
 * Utility API allowing specification of a different default flag value depending on the release channel of Android Studio.
 *
 * Example usage: `ChannelDefault.of(100).withBetaOverride(200)`.
 */
class ChannelDefault<T>
private constructor(default: T, private val versionProvider: (() -> String)? = null) : Supplier<T> {

  private var value: T = default

  override fun get(): T = value

  fun withDevOverride(override: T): ChannelDefault<T> {
    if (IdeChannel.getChannel(versionProvider) == IdeChannel.Channel.DEV) value = override
    return this
  }

  fun withNightlyOverride(override: T): ChannelDefault<T> {
    if (IdeChannel.getChannel(versionProvider) == IdeChannel.Channel.NIGHTLY) value = override
    return this
  }

  fun withCanaryOverride(override: T): ChannelDefault<T> {
    if (IdeChannel.getChannel(versionProvider) == IdeChannel.Channel.CANARY) value = override
    return this
  }

  fun withBetaOverride(override: T): ChannelDefault<T> {
    if (IdeChannel.getChannel(versionProvider) == IdeChannel.Channel.BETA) value = override
    return this
  }

  fun withRCOverride(override: T): ChannelDefault<T> {
    if (IdeChannel.getChannel(versionProvider) == IdeChannel.Channel.RC) value = override
    return this
  }

  fun withStableOverride(override: T): ChannelDefault<T> {
    if (IdeChannel.getChannel(versionProvider) == IdeChannel.Channel.STABLE) value = override
    return this
  }

  companion object {
    @JvmStatic fun <T> of(default: T) = ChannelDefault(default)

    @VisibleForTesting
    fun <T> of(default: T, versionProvider: (() -> String)) =
      ChannelDefault(default, versionProvider)
  }
}
