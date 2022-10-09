/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.cpu.nodemodel.NoSymbolModel
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BaseCpuCaptureTest {
  @Test
  fun updateClockType() {
    val capture = BaseCpuCapture(42, Trace.TraceType.ART, Range(0.0, 1.0), CAPTURE_TREES)
    assertClockType(capture.captureNodes, ClockType.GLOBAL)
    capture.updateClockType(ClockType.THREAD)
    assertClockType(capture.captureNodes, ClockType.THREAD)
  }

  @Test
  fun `nodes with tags to collapse are collapsed and can be restored`() {
    val captureTrees = mapOf<CpuThreadInfo, CaptureNode>(
      CpuThreadSliceInfo(1, "main", 1, "foo") to CaptureNode(SingleNameModel("foo")).apply {
        addChild(CaptureNode(NoSymbolModel("path","bar")).apply {
          addChild(CaptureNode(SingleNameModel("foobar")))
        })
      },
      CpuThreadSliceInfo(2, "thread-2", 1, "foo") to CaptureNode(SingleNameModel("foo")).apply {
        addChild(CaptureNode(SingleNameModel("bar")))
      }
    )
    val capture = BaseCpuCapture(42, Trace.TraceType.SIMPLEPERF, true, null, Range(0.0, 1.0), captureTrees,
                                 setOf("path"))
    assertThat(capture.getCaptureNode(1)!!.getChildAt(0).data is NoSymbolModel)
    capture.collapseNodesWithTags(setOf("path"))
    assertThat(capture.getCaptureNode(1)!!.getChildAt(0).data !is NoSymbolModel)
    capture.collapseNodesWithTags(setOf())
    assertThat(capture.getCaptureNode(1)!!.getChildAt(0).data is NoSymbolModel)
  }

  /**
   * Recursively asserts the clock type of the given nodes and their children nodes.
   */
  private fun assertClockType(nodes: Collection<CaptureNode>, clockType: ClockType) {
    nodes.forEach {
      assertThat(it.clockType).isEqualTo(clockType)
      assertClockType(it.children, clockType)
    }
  }

  companion object {
    val CAPTURE_TREES = mutableMapOf<CpuThreadInfo, CaptureNode>(
      CpuThreadSliceInfo(1, "main", 1, "foo") to CaptureNode(SingleNameModel("foo")).apply {
        addChild(CaptureNode(SingleNameModel("bar")).apply {
          addChild(CaptureNode(SingleNameModel("foobar")))
        })
      },
      CpuThreadSliceInfo(2, "thread-2", 1, "foo") to CaptureNode(SingleNameModel("foo")).apply {
        addChild(CaptureNode(SingleNameModel("bar")))
      }
    )
  }
}