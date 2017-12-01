// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.cpu

import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.cpu.nodemodel.*
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.UIUtil
import org.junit.Assert.fail
import org.junit.Test
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

class MethodModelHRendererTest {

  @Test
  fun renderInvalidNodeShouldThrowException() {
    val unsupportedNode = CaptureNode()
    unsupportedNode.captureNodeModel = SingleNameModel("write")
    val renderer = MethodModelHRenderer(CaptureModel.Details.Type.CALL_CHART)

    val fakeGraphics = UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
    try {
      renderer.render(fakeGraphics, unsupportedNode, Rectangle2D.Float())
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Node type not supported.")
    }
  }

  @Test
  fun invalidChartTypeShouldThrowException() {
    try {
      MethodModelHRenderer(CaptureModel.Details.Type.BOTTOM_UP)
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Chart type not supported and can't be rendered.")
    }
  }

  @Test
  fun testFilterRenderStyle() {
    val simpleNode = CaptureNode()
    simpleNode.captureNodeModel = SyscallModel("write")
    val renderer = MethodModelHRenderer(CaptureModel.Details.Type.CALL_CHART)

    val fakeGraphics = UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
    fakeGraphics.paint = Color.RED
    simpleNode.filterType = CaptureNode.FilterType.MATCH
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float())
    assertThat(fakeGraphics.paint).isEqualTo(Color.BLACK)
    assertThat(fakeGraphics.font.isBold).isFalse()

    fakeGraphics.paint = Color.RED
    simpleNode.filterType = CaptureNode.FilterType.UNMATCH
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float())
    assertThat(fakeGraphics.paint).isEqualTo(MethodModelHRenderer.toUnmatchColor(Color.BLACK))
    assertThat(fakeGraphics.font.isBold).isFalse()

    fakeGraphics.paint = Color.RED
    simpleNode.filterType = CaptureNode.FilterType.EXACT_MATCH
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float())
    assertThat(fakeGraphics.paint).isEqualTo(Color.BLACK)
    // TODO: refactor MethodModelHRenderer#render to check font is Bold for EXACT_MATCHES

    // TODO: refactor MethodModelHRenderer#render to cover the case of null filter type
  }

  @Test
  fun testJavaColors() {
    val invalidModel = SyscallModel("write")
    try {
      JavaMethodHChartColors.getBorderColor(invalidModel, CaptureModel.Details.Type.CALL_CHART, false)
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Model must be an instance of JavaMethodModel.")
    }

    val vendorModel = JavaMethodModel("toString", "java.lang.String")
    doTestJavaMethodColors(vendorModel, ProfilerColors.CPU_CALLCHART_VENDOR, ProfilerColors.CPU_CALLCHART_VENDOR_BORDER,
        ProfilerColors.CPU_FLAMECHART_VENDOR, ProfilerColors.CPU_FLAMECHART_VENDOR_BORDER)

    val platformModel = JavaMethodModel("inflate", "com.android.Activity")
    doTestJavaMethodColors(platformModel, ProfilerColors.CPU_CALLCHART_PLATFORM, ProfilerColors.CPU_CALLCHART_PLATFORM_BORDER,
        ProfilerColors.CPU_FLAMECHART_PLATFORM, ProfilerColors.CPU_FLAMECHART_PLATFORM_BORDER)

    val appModel = JavaMethodModel("toString", "com.example.MyClass")
    doTestJavaMethodColors(appModel, ProfilerColors.CPU_CALLCHART_APP, ProfilerColors.CPU_CALLCHART_APP_BORDER,
        ProfilerColors.CPU_FLAMECHART_APP, ProfilerColors.CPU_FLAMECHART_APP_BORDER)
  }

  @Test
  fun testNativeColors() {
    val invalidModel = JavaMethodModel("toString", "com.example.MyClass")
    try {
      NativeModelHChartColors.getBorderColor(invalidModel, CaptureModel.Details.Type.CALL_CHART, false)
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Model must be a subclass of NativeNodeModel.")
    }

    val vendorModel = CppFunctionModel.Builder("Load").setClassOrNamespace("openjdkjvmti").build()
    doTestNativeColors(vendorModel, ProfilerColors.CPU_CALLCHART_VENDOR, ProfilerColors.CPU_CALLCHART_VENDOR_BORDER,
        ProfilerColors.CPU_FLAMECHART_VENDOR, ProfilerColors.CPU_FLAMECHART_VENDOR_BORDER)

    val platformModel = CppFunctionModel.Builder("Inflate").setClassOrNamespace("android::Activity").build()
    doTestNativeColors(platformModel, ProfilerColors.CPU_CALLCHART_PLATFORM, ProfilerColors.CPU_CALLCHART_PLATFORM_BORDER,
        ProfilerColors.CPU_FLAMECHART_PLATFORM, ProfilerColors.CPU_FLAMECHART_PLATFORM_BORDER)

    val appModel = CppFunctionModel.Builder("DoFrame").setClassOrNamespace("PlayScene").build()
    doTestNativeColors(appModel, ProfilerColors.CPU_CALLCHART_APP, ProfilerColors.CPU_CALLCHART_APP_BORDER,
        ProfilerColors.CPU_FLAMECHART_APP, ProfilerColors.CPU_FLAMECHART_APP_BORDER)
  }

  private fun doTestJavaMethodColors(model: JavaMethodModel, callChartFill: Color, callChartBorder: Color, flameChartFill: Color,
                                     flameChartBorder: Color) {
    val callChart = CaptureModel.Details.Type.CALL_CHART
    val flameChart = CaptureModel.Details.Type.FLAME_CHART

    // Call chart not unmatched
    var color = JavaMethodHChartColors.getFillColor(model, callChart, false)
    assertThat(color).isEqualTo(callChartFill)
    color = JavaMethodHChartColors.getBorderColor(model, callChart, false)
    assertThat(color).isEqualTo(callChartBorder)
    // Call chart unmatched
    color = JavaMethodHChartColors.getFillColor(model, callChart, true)
    assertThat(color).isEqualTo(MethodModelHRenderer.toUnmatchColor(callChartFill))
    color = JavaMethodHChartColors.getBorderColor(model, callChart, true)
    assertThat(color).isEqualTo(MethodModelHRenderer.toUnmatchColor(callChartBorder))
    // Flame chart not unmatched
    color = JavaMethodHChartColors.getFillColor(model, flameChart, false)
    assertThat(color).isEqualTo(flameChartFill)
    color = JavaMethodHChartColors.getBorderColor(model, flameChart, false)
    assertThat(color).isEqualTo(flameChartBorder)
    // Flame chart unmatched
    color = JavaMethodHChartColors.getFillColor(model, flameChart, true)
    assertThat(color).isEqualTo(MethodModelHRenderer.toUnmatchColor(flameChartFill))
    color = JavaMethodHChartColors.getBorderColor(model, flameChart, true)
    assertThat(color).isEqualTo(MethodModelHRenderer.toUnmatchColor(flameChartBorder))
  }

  private fun doTestNativeColors(model: NativeNodeModel, callChartFill: Color, callChartBorder: Color, flameChartFill: Color,
                                 flameChartBorder: Color) {
    val callChart = CaptureModel.Details.Type.CALL_CHART
    val flameChart = CaptureModel.Details.Type.FLAME_CHART

    // Call chart not unmatched
    var color = NativeModelHChartColors.getFillColor(model, callChart, false)
    assertThat(color).isEqualTo(callChartFill)
    color = NativeModelHChartColors.getBorderColor(model, callChart, false)
    assertThat(color).isEqualTo(callChartBorder)
    // Call chart unmatched
    color = NativeModelHChartColors.getFillColor(model, callChart, true)
    assertThat(color).isEqualTo(MethodModelHRenderer.toUnmatchColor(callChartFill))
    color = NativeModelHChartColors.getBorderColor(model, callChart, true)
    assertThat(color).isEqualTo(MethodModelHRenderer.toUnmatchColor(callChartBorder))
    // Flame chart not unmatched
    color = NativeModelHChartColors.getFillColor(model, flameChart, false)
    assertThat(color).isEqualTo(flameChartFill)
    color = NativeModelHChartColors.getBorderColor(model, flameChart, false)
    assertThat(color).isEqualTo(flameChartBorder)
    // Flame chart unmatched
    color = NativeModelHChartColors.getFillColor(model, flameChart, true)
    assertThat(color).isEqualTo(MethodModelHRenderer.toUnmatchColor(flameChartFill))
    color = NativeModelHChartColors.getBorderColor(model, flameChart, true)
    assertThat(color).isEqualTo(MethodModelHRenderer.toUnmatchColor(flameChartBorder))
  }
}
