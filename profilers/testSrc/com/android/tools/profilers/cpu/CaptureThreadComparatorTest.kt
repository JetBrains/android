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

class CaptureThreadComparatorTest {
  @Test
  fun childSizeOrdering() {
    val capture = Mockito.mock(CpuCapture::class.java)

    Mockito.`when`(capture.getCaptureNode(1)).thenReturn(buildCaptureNode("Thread 1", 5))
    Mockito.`when`(capture.getCaptureNode(2)).thenReturn(buildCaptureNode("Thread 2", 0))
    val comparator = CaptureThreadComparator(capture)
    var result = comparator.compare(CpuThreadInfo(1, "Thread 1"), CpuThreadInfo(2, "Thread 2"))
    assertThat(result).isLessThan(0)
    result = comparator.compare(CpuThreadInfo(1, "Thread 1"), CpuThreadInfo(1, "Thread 1"))
    assertThat(result).isEqualTo(0)
  }

  fun buildCaptureNode(name: String, numChildren: Int): CaptureNode {
    val node = CaptureNode(SingleNameModel(name))
    for (i in 0 until numChildren) {
      node.addChild(buildCaptureNode(name + "i", 0))
    }
    return node
  }
}