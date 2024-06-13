/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.interactive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Creates a [StateFlow] returning the FPS limit to be applied from a given [StateFlow] of booleans.
 * When the value from [this] is true, the FPS limit is throttled to [standardFpsLimit] / 3
 * otherwise it is not and [standardFpsLimit] is returned.
 */
fun StateFlow<Boolean>.fpsLimitFlow(
  coroutineScope: CoroutineScope,
  standardFpsLimit: Int,
): StateFlow<Int> {
  return map { fpsLimit(standardFpsLimit, it) }
    .stateIn(coroutineScope, SharingStarted.Eagerly, fpsLimit(standardFpsLimit, value))
}

private fun fpsLimit(standardFpsLimit: Int, isThrottled: Boolean) =
  if (isThrottled) standardFpsLimit / 3 else standardFpsLimit
