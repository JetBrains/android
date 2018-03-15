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
import com.intellij.ui.Graphics2DDelegate
import com.intellij.util.ui.UIUtil
import org.junit.Assert.fail
import org.junit.Test
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Shape
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

class CaptureNodeHRendererTest {

  @Test
  fun renderIdleCpuTime() {
    val simpleNode = CaptureNode(AtraceNodeModel("SomeName"))
    simpleNode.startGlobal = 10;
    simpleNode.endGlobal = 20;
    simpleNode.startThread = 10;
    simpleNode.endThread = 15;
    val renderer = CaptureNodeHRenderer(CaptureModel.Details.Type.CALL_CHART)
    val renderWindow = Rectangle2D.Float(0.0f, 0.0f, 10.0f,10.0f)
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, false)
    assertThat(fakeGraphics.fillShapes).hasSize(2)

    // Validate we get two shapes drawn, the first one is the expected size of the window.
    // The second on is the size of the idle time. In this case half our global time.
    fakeGraphics.expectFillShapes(renderWindow, Rectangle2D.Double(5.0, 0.0, 5.0, 10.0))
  }

  @Test
  fun renderIdleCpuTimeDoesNotHappenOnOtherModels() {
    val simpleNode = CaptureNode(SingleNameModel("SomeName"))
    simpleNode.startGlobal = 10;
    simpleNode.endGlobal = 20;
    simpleNode.startThread = 10;
    simpleNode.endThread = 15;
    val renderer = CaptureNodeHRenderer(CaptureModel.Details.Type.CALL_CHART)
    val renderWindow = Rectangle2D.Float(0.0f, 0.0f, 10.0f,10.0f)
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, false)
    assertThat(fakeGraphics.fillShapes).hasSize(1)

    // Validate we get the expected size of the window.
    fakeGraphics.expectFillShapes(renderWindow)
  }

  @Test
  fun renderInvalidNodeShouldThrowException() {
    val unsupportedNode = CaptureNode(StubCaptureNodeModel())
    val renderer = CaptureNodeHRenderer(CaptureModel.Details.Type.CALL_CHART)

    val fakeGraphics = TestGraphics2D()
    try {
      renderer.render(fakeGraphics, unsupportedNode, Rectangle2D.Float(), false)
      fail()
    }
    catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Node type not supported.")
    }
  }

  @Test
  fun invalidChartTypeShouldThrowException() {
    try {
      CaptureNodeHRenderer(CaptureModel.Details.Type.BOTTOM_UP)
      fail()
    }
    catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Chart type not supported and can't be rendered.")
    }
  }

  @Test
  fun testFilterRenderStyle() {
    val simpleNode = CaptureNode(SyscallModel("write"))
    val renderer = CaptureNodeHRenderer(CaptureModel.Details.Type.CALL_CHART)

    val fakeGraphics = TestGraphics2D()
    fakeGraphics.paint = Color.RED
    simpleNode.filterType = CaptureNode.FilterType.MATCH
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float(), false)
    assertThat(fakeGraphics.paint).isEqualTo(Color.BLACK)
    assertThat(fakeGraphics.font.isBold).isFalse()

    fakeGraphics.paint = Color.RED
    simpleNode.filterType = CaptureNode.FilterType.UNMATCH
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float(), false)
    assertThat(fakeGraphics.paint).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(Color.BLACK))
    assertThat(fakeGraphics.font.isBold).isFalse()

    fakeGraphics.paint = Color.RED
    simpleNode.filterType = CaptureNode.FilterType.EXACT_MATCH
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float(), false)
    assertThat(fakeGraphics.paint).isEqualTo(Color.BLACK)
    // TODO: refactor CaptureNodeHRenderer#render to check font is Bold for EXACT_MATCHES

    // TODO: refactor CaptureNodeHRenderer#render to cover the case of null filter type
  }

  @Test
  fun testJavaColors() {
    val invalidModel = SyscallModel("write")
    try {
      JavaMethodHChartColors.getBorderColor(invalidModel, CaptureModel.Details.Type.CALL_CHART, false)
      fail()
    }
    catch (e: IllegalStateException) {
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
    }
    catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Model must be a subclass of NativeNodeModel.")
    }

    val vendorModel = CppFunctionModel.Builder("Load").setIsUserCode(false).setClassOrNamespace("glClear").build()
    doTestNativeColors(vendorModel, ProfilerColors.CPU_CALLCHART_VENDOR, ProfilerColors.CPU_CALLCHART_VENDOR_BORDER,
        ProfilerColors.CPU_FLAMECHART_VENDOR, ProfilerColors.CPU_FLAMECHART_VENDOR_BORDER)

    val platformModel = CppFunctionModel.Builder("Inflate").setIsUserCode(false).setClassOrNamespace("android::Activity").build()
    doTestNativeColors(platformModel, ProfilerColors.CPU_CALLCHART_PLATFORM, ProfilerColors.CPU_CALLCHART_PLATFORM_BORDER,
        ProfilerColors.CPU_FLAMECHART_PLATFORM, ProfilerColors.CPU_FLAMECHART_PLATFORM_BORDER)

    val appModel = CppFunctionModel.Builder("DoFrame").setIsUserCode(true).setClassOrNamespace("PlayScene").build()
    doTestNativeColors(appModel, ProfilerColors.CPU_CALLCHART_APP, ProfilerColors.CPU_CALLCHART_APP_BORDER,
        ProfilerColors.CPU_FLAMECHART_APP, ProfilerColors.CPU_FLAMECHART_APP_BORDER)
  }

  @Test
  fun testFittingTextForJavaMethod() {
    checkFittingText(nodeModel = JavaMethodModel("toString", "com.example.MyClass"), expectedTexts = listOf(
        "com.example.MyClass.toString",
        "c.example.MyClass.toString",
        "c.e.MyClass.toString",
        "c.e.M.toString",
        "toString",
        "toSt..."
    ))
  }

  @Test
  fun testFittingTextForNativeMethod() {
    checkFittingText(nodeModel = CppFunctionModel.Builder("myNativeMethod").setParameters("int, float")
        .setClassOrNamespace("MyNativeClass").build(), expectedTexts = listOf(
        "MyNativeClass::myNativeMethod",
        "M::myNativeMethod",
        "myNativeMethod",
        "myNativeMe..."
    ))
  }

  private fun checkFittingText(nodeModel: CaptureNodeModel, expectedTexts: List<String>) {
    val node = CaptureNode(nodeModel)
    val textFitPredicate = TestTextFitPredicate()
    val renderer = CaptureNodeHRenderer(CaptureModel.Details.Type.CALL_CHART, textFitPredicate)
    val graphics = TestGraphics2D()

    var prevTextLength = expectedTexts[0].length + 1
    for (text in expectedTexts) {
      textFitPredicate.fittingLength = prevTextLength - 1
      renderer.render(graphics, node, Rectangle2D.Float(), false)
      assertThat(graphics.drawnString).isEqualTo(text)
      prevTextLength = text.length
    }
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
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(callChartFill))
    color = JavaMethodHChartColors.getBorderColor(model, callChart, true)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(callChartBorder))
    // Flame chart not unmatched
    color = JavaMethodHChartColors.getFillColor(model, flameChart, false)
    assertThat(color).isEqualTo(flameChartFill)
    color = JavaMethodHChartColors.getBorderColor(model, flameChart, false)
    assertThat(color).isEqualTo(flameChartBorder)
    // Flame chart unmatched
    color = JavaMethodHChartColors.getFillColor(model, flameChart, true)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(flameChartFill))
    color = JavaMethodHChartColors.getBorderColor(model, flameChart, true)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(flameChartBorder))
  }

  private fun doTestNativeColors(model: NativeNodeModel, callChartFill: Color, callChartBorder: Color,
                                 flameChartFill: Color, flameChartBorder: Color) {
    val callChart = CaptureModel.Details.Type.CALL_CHART
    val flameChart = CaptureModel.Details.Type.FLAME_CHART

    // Call chart not unmatched
    var color = NativeModelHChartColors.getFillColor(model, callChart, false)
    assertThat(color).isEqualTo(callChartFill)
    color = NativeModelHChartColors.getBorderColor(model, callChart, false)
    assertThat(color).isEqualTo(callChartBorder)
    // Call chart unmatched
    color = NativeModelHChartColors.getFillColor(model, callChart, true)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(callChartFill))
    color = NativeModelHChartColors.getBorderColor(model, callChart, true)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(callChartBorder))
    // Flame chart not unmatched
    color = NativeModelHChartColors.getFillColor(model, flameChart, false)
    assertThat(color).isEqualTo(flameChartFill)
    color = NativeModelHChartColors.getBorderColor(model, flameChart, false)
    assertThat(color).isEqualTo(flameChartBorder)
    // Flame chart unmatched
    color = NativeModelHChartColors.getFillColor(model, flameChart, true)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(flameChartFill))
    color = NativeModelHChartColors.getBorderColor(model, flameChart, true)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(flameChartBorder))
  }

  private class TestGraphics2D : Graphics2DDelegate(UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()) {
    var drawnString: String? = null
    val fillShapes: MutableList<Shape> = ArrayList()

    override fun drawString(s: String, x: Float, y: Float) {
      drawnString = s
    }

    override fun fill(s: Shape) {
      fillShapes.add(s)
    }

    fun expectFillShapes(vararg rectangles: Rectangle2D) {
      for (i in rectangles.indices) {
        val boundsA = fillShapes[i].bounds2D;
        val boundsB = rectangles[i];
        assertThat(boundsA.x).isWithin(0.0001).of(boundsB.x)
        assertThat(boundsA.y).isWithin(0.0001).of(boundsB.y)
        assertThat(boundsA.width).isWithin(0.0001).of(boundsB.width)
        assertThat(boundsA.height).isWithin(0.0001).of(boundsB.height)
      }
    }
  }

  private class TestTextFitPredicate: CaptureNodeHRenderer.TextFitsPredicate {
    var fittingLength: Int = 0
    override fun test(text: String, metrics: FontMetrics, width: Float) = text.length <= fittingLength
  }

  private class StubCaptureNodeModel : CaptureNodeModel {
    override fun getName(): String {
      throw UnsupportedOperationException()
    }

    override fun getFullName(): String {
      throw UnsupportedOperationException()
    }

    override fun getId(): String {
      throw UnsupportedOperationException()
    }
  }
}
