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
@file:JvmName("ChannelDefault")
package com.android.tools.idea.flags

import com.android.tools.idea.IdeChannel
import com.google.common.annotations.VisibleForTesting

/**
 * Utility API allowing specification of a different default flag value depending on the release channel of Android Studio.
 *
 * Example usage: `ChannelDefault.enabledUpTo(CANARY)` would return true for dev, nightly and canary, but false for beta, release-candidate
 * and stable versions of Studio.
 */
internal fun enabledUpTo(leastStableChannel: IdeChannel.Channel) : Boolean = enabledUpTo(leastStableChannel, null)


@VisibleForTesting
internal fun enabledUpTo(leastStableChannel: IdeChannel.Channel, versionProvider: (() -> String)?) : Boolean {
  check(leastStableChannel <= IdeChannel.Channel.CANARY || leastStableChannel == IdeChannel.Channel.STABLE) {
    "Flags must not be conditional between Beta, RC and Stable"
  }
  return IdeChannel.getChannel(versionProvider) <= leastStableChannel
}