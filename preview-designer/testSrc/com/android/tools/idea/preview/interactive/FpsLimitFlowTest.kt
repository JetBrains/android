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

import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FpsLimitFlowTest {

  private val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())

  @Test
  fun testFpsLimitIsInitialized() {
    assertEquals(10, MutableStateFlow(true).fpsLimitFlow(scope, 30).value)
    assertEquals(30, MutableStateFlow(false).fpsLimitFlow(scope, 30).value)
  }

  @Test
  fun testFpsLimitIsUpdated(): Unit = runBlocking {
    val throttlingSource = MutableStateFlow(true)

    val fpsLimits = scope.async { throttlingSource.fpsLimitFlow(scope, 30).take(3).toList() }

    throttlingSource.value = false
    throttlingSource.value = true

    assertEquals(listOf(10, 30, 10), fpsLimits.await())
  }
}
