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
import java.util.function.Supplier
import org.jetbrains.annotations.VisibleForTesting

/**
 * Utility API allowing specification of a different default flag value depending on the release channel of Android Studio.
 *
 * Example usage: `ChannelDefault.of(100).withOverride(200, BETA)`.
 */
class ChannelDefault<T>
private constructor(default: T, private val versionProvider: (() -> String)? = null) : Supplier<T> {

  private var value: T = default

  override fun get(): T = value

  fun withOverride(override: T, channel: IdeChannel.Channel, vararg moreChannels: IdeChannel.Channel) = apply {
    val ideChannel = IdeChannel.getChannel(versionProvider)
    if (ideChannel == channel || ideChannel in moreChannels) value = override
  }

  fun withOverride(override: T, channels: ClosedRange<IdeChannel.Channel>) = apply {
    if (IdeChannel.getChannel(versionProvider) in channels) value = override
  }

  companion object {
    @JvmStatic fun <T> of(default: T) = ChannelDefault(default)

    @VisibleForTesting
    fun <T> of(default: T, versionProvider: (() -> String)) =
      ChannelDefault(default, versionProvider)
  }
}
