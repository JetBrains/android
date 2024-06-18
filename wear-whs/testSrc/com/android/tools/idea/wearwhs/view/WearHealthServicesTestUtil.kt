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
package com.android.tools.idea.wearwhs.view

import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.wearwhs.view.WearHealthServicesStateManagerTest.Companion.TEST_MAX_WAIT_TIME_SECONDS
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.StateFlow

internal suspend fun <T> StateFlow<T>.waitForValue(
  value: T,
  timeoutSeconds: Long = TEST_MAX_WAIT_TIME_SECONDS,
) {
  awaitStatus("Timeout waiting for value $value", timeoutSeconds.seconds) { it == value }
}
