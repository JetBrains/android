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

import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class CpuThreadComparatorTest {
  @Test
  fun childSizeOrdering() {
    val capture = Mockito.mock(CpuCapture::class.java)

    whenever(capture.getCaptureNode(1)).thenReturn(buildCaptureNode("Thread 1", 5))
    whenever(capture.getCaptureNode(2)).thenReturn(buildCaptureNode("Thread 2", 0))
    whenever(capture.getCaptureNode(3)).thenReturn(buildCaptureNode("Thread 3", 1))
    whenever(capture.getCaptureNode(4)).thenReturn(buildCaptureNode(CpuThreadInfo.RENDER_THREAD_NAME, 0))
    val comparator = CpuThreadComparator.withCaptureInfo(capture)
    val thread1 = CpuThreadInfo(1, "Thread 1")
    val thread2 = CpuThreadInfo(2, "Thread 2")
    val thread3 = CpuThreadInfo(3, "Thread 3")
    val renderingThread = CpuThreadInfo(3, CpuThreadInfo.RENDER_THREAD_NAME)
    assertThat(listOf(thread1, thread2, thread3, renderingThread).sortedWith(comparator))
      .isEqualTo(listOf(renderingThread, thread1, thread3, thread2))
  }

  fun buildCaptureNode(name: String, numChildren: Int): CaptureNode {
    val node = CaptureNode(SingleNameModel(name))
    for (i in 0 until numChildren) {
      node.addChild(buildCaptureNode(name + "i", 0))
    }
    return node
  }
}