/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Creates a [MutableSharedFlow] that emits only the most recent value published after a subscriber
 * starts collecting. This ensures that new collectors receive only future emissions and do not
 * retain past values.
 *
 * @return A [MutableSharedFlow] that buffers only the latest emitted value, dropping older values
 *   if a new one arrives before being collected.
 */
fun <T> ephemeralFlow(): MutableSharedFlow<T?> {
  return MutableSharedFlow<T?>(
    // When a new collector starts, it only sees future events.
    replay = 0,
    // Store only one event at a time.
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
}
