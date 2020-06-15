/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuThreadInfoTest {
  companion object {
    val MAIN_THREAD = CpuThreadInfo(123, "foo", true)
    val RENDER_THREAD_10 = CpuThreadInfo(10, "RenderThread")
    val RENDER_THREAD_20 = CpuThreadInfo(20, "RenderThread")
  }

  @Test
  fun notMainThreadByDefault() {
    val thread = CpuThreadInfo(1, "1")
    assertThat(thread.isMainThread).isFalse()
  }

  @Test
  fun testRenderThread() {
    val notRenderThread = CpuThreadInfo(1, "Render")
    assertThat(RENDER_THREAD_10.isRenderThread).isTrue()
    assertThat(notRenderThread.isRenderThread).isFalse()
  }

  @Test
  fun testThreadSorted() {
    val thread1 = CpuThreadInfo(1, "1")
    val thread2 = CpuThreadInfo(2, "Thread")
    val thread3 = CpuThreadInfo(3, "Thread")
    val threadArray = mutableListOf(thread3, thread2, thread1, RENDER_THREAD_20, MAIN_THREAD, RENDER_THREAD_10)
    threadArray.sort()

    // Expected order:
    // Main thread
    // Render threads (ordered by thread ID)
    // Other threads (ordered by name then thread ID)
    assertThat(threadArray).containsExactly(MAIN_THREAD, RENDER_THREAD_10, RENDER_THREAD_20, thread1, thread2, thread3).inOrder()
  }
}