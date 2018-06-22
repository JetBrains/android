/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.cpu.CaptureModel
import com.android.tools.profilers.cpu.CaptureNode
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
    renderer.render(fakeGraphics, simpleNode, renderWindow, renderWindow, false)
    assertThat(fakeGraphics.fillShapes).hasSize(2)

    // Validate we get two shapes drawn, the first one is the expected size of the window.
    // The second on is the size of the idle time. In this case half our global time.
    fakeGraphics.expectFillShapes(renderWindow, Rectangle2D.Double(5.0, 0.0, 5.0, 10.0))
  }

  @Test
  fun renderUseClampedRenderWindowForSizing() {
    val simpleNode = CaptureNode(AtraceNodeModel("SomeName")).apply {
      startGlobal = 10
      endGlobal = 20
      startThread = 10
      endThread = 15
    }
    val renderer = CaptureNodeHRenderer(CaptureModel.Details.Type.CALL_CHART)
    // Test clamp width.
    var renderWindow = Rectangle2D.Float(0.0f, 0.0f, 10.0f,10.0f)
    var clampedRenderWindow = Rectangle2D.Float(0.0f, 0.0f, 6.0f, 6.0f)
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, clampedRenderWindow, false)
    assertThat(fakeGraphics.fillShapes).hasSize(2)

    // Validate we get two shapes drawn, the first one is the expected size of the clamped window.
    // The second on is the size of the idle time clamped to the window size.
    fakeGraphics.expectFillShapes(clampedRenderWindow, Rectangle2D.Double(5.0, 0.0, 1.0, 10.0))
    fakeGraphics.fillShapes.clear();

    // Test clamp start and width
    renderWindow = Rectangle2D.Float(0.0f, 0.0f, 10.0f,10.0f)
    clampedRenderWindow = Rectangle2D.Float(3.0f, 0.0f, 1.0f, 6.0f)
    renderer.render(fakeGraphics, simpleNode, renderWindow, clampedRenderWindow, false)
    assertThat(fakeGraphics.fillShapes).hasSize(2)

    // Validate we get two shapes drawn, the first one is the expected size of the clamped window.
    // The second on is the size of the idle time clamped to the window size.
    fakeGraphics.expectFillShapes(clampedRenderWindow, Rectangle2D.Double(4.0, 0.0, 0.0, 10.0))
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
    renderer.render(fakeGraphics, simpleNode, renderWindow, renderWindow,false)
    assertThat(fakeGraphics.fillShapes).hasSize(1)

    // Validate we get the expected size of the window.
    fakeGraphics.expectFillShapes(renderWindow)
  }

  @Test
  fun renderIdleTimeWithNegativeStartFillsIdleTime() {
    val simpleNode = CaptureNode(AtraceNodeModel("SomeName"))
    simpleNode.startGlobal = 10;
    simpleNode.endGlobal = 110;
    simpleNode.startThread = 10;
    simpleNode.endThread = 15;
    val renderer = CaptureNodeHRenderer(CaptureModel.Details.Type.CALL_CHART)
    val renderWindow = Rectangle2D.Float(-100.0f, 0.0f, 300.0f,10.0f)
    val clampWindow = Rectangle2D.Float(0f, 0f, 10f, 10f);
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, clampWindow,false)
    assertThat(fakeGraphics.fillShapes).hasSize(2)

    // Validate we get the expected size of the window.
    // Our first render should be only 10px on screen,
    // The second (idle) should be this entire 10px.
    fakeGraphics.expectFillShapes(clampWindow, clampWindow)
  }

  @Test
  fun renderInvalidNodeShouldThrowException() {
    val unsupportedNode = CaptureNode(StubCaptureNodeModel())
    val renderer = CaptureNodeHRenderer(CaptureModel.Details.Type.CALL_CHART)

    val fakeGraphics = TestGraphics2D()
    try {
      renderer.render(fakeGraphics, unsupportedNode, Rectangle2D.Float(), Rectangle2D.Float(), false)
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
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float(), Rectangle2D.Float(), false)
    assertThat(fakeGraphics.paint).isEqualTo(Color.BLACK)
    assertThat(fakeGraphics.font.isBold).isFalse()

    fakeGraphics.paint = Color.RED
    simpleNode.filterType = CaptureNode.FilterType.UNMATCH
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float(), Rectangle2D.Float(), false)
    assertThat(fakeGraphics.paint).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(Color.BLACK))
    assertThat(fakeGraphics.font.isBold).isFalse()

    fakeGraphics.paint = Color.RED
    simpleNode.filterType = CaptureNode.FilterType.EXACT_MATCH
    renderer.render(fakeGraphics, simpleNode, Rectangle2D.Float(), Rectangle2D.Float(), false)
    assertThat(fakeGraphics.paint).isEqualTo(Color.BLACK)
    // TODO: refactor CaptureNodeHRenderer#render to check font is Bold for EXACT_MATCHES

    // TODO: refactor CaptureNodeHRenderer#render to cover the case of null filter type
  }

  @Test
  fun testAtraceColors() {
    val invalidModel = SyscallModel("write")
    try {
      AtraceNodeModelHChartColors.getFillColor(invalidModel, CaptureModel.Details.Type.CALL_CHART, false, false)
      fail()
    }
    catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Model must be an instance of AtraceNodeModel.")
    }

    val model = AtraceNodeModel("SomeName")

    var color = AtraceNodeModelHChartColors.getFillColor(model, CaptureModel.Details.Type.CALL_CHART, false, false)
    assertThat(color).isEqualTo(ProfilerColors.CPU_USAGE_CAPTURED)

    color = AtraceNodeModelHChartColors.getFillColor(model, CaptureModel.Details.Type.CALL_CHART, false, true)
    assertThat(color).isEqualTo(ProfilerColors.CPU_USAGE_CAPTURED_HOVER)

    color = AtraceNodeModelHChartColors.getFillColor(model, CaptureModel.Details.Type.FLAME_CHART, false, false)
    assertThat(color).isEqualTo(ProfilerColors.CPU_FLAMECHART_APP)

    color = AtraceNodeModelHChartColors.getFillColor(model, CaptureModel.Details.Type.FLAME_CHART, false, true)
    assertThat(color).isEqualTo(ProfilerColors.CPU_FLAMECHART_APP_HOVER)

    color = AtraceNodeModelHChartColors.getIdleCpuColor(model, CaptureModel.Details.Type.CALL_CHART, false, false)
    assertThat(color).isEqualTo(ProfilerColors.CPU_TRACE_IDLE)

    color = AtraceNodeModelHChartColors.getIdleCpuColor(model, CaptureModel.Details.Type.CALL_CHART, false, true)
    assertThat(color).isEqualTo(ProfilerColors.CPU_TRACE_IDLE_HOVER)

    color = AtraceNodeModelHChartColors.getIdleCpuColor(model, CaptureModel.Details.Type.FLAME_CHART, false, false)
    assertThat(color).isEqualTo(ProfilerColors.CPU_FLAMECHART_APP)

    color = AtraceNodeModelHChartColors.getIdleCpuColor(model, CaptureModel.Details.Type.FLAME_CHART, false, true)
    assertThat(color).isEqualTo(ProfilerColors.CPU_FLAMECHART_APP_HOVER)
  }

  @Test
  fun testJavaColors() {
    val invalidModel = SyscallModel("write")
    try {
      JavaMethodHChartColors.getFillColor(invalidModel, CaptureModel.Details.Type.CALL_CHART, false, false)
      fail()
    }
    catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Model must be an instance of JavaMethodModel.")
    }

    val vendorModel = JavaMethodModel("toString", "java.lang.String")
    doTestJavaMethodColors(vendorModel, ProfilerColors.CPU_CALLCHART_VENDOR, ProfilerColors.CPU_FLAMECHART_VENDOR,
                           ProfilerColors.CPU_CALLCHART_VENDOR_HOVER, ProfilerColors.CPU_FLAMECHART_VENDOR_HOVER)

    val platformModel = JavaMethodModel("inflate", "com.android.Activity")
    doTestJavaMethodColors(platformModel, ProfilerColors.CPU_CALLCHART_PLATFORM, ProfilerColors.CPU_FLAMECHART_PLATFORM,
                           ProfilerColors.CPU_CALLCHART_PLATFORM_HOVER, ProfilerColors.CPU_FLAMECHART_PLATFORM_HOVER)

    val appModel = JavaMethodModel("toString", "com.example.MyClass")
    doTestJavaMethodColors(appModel, ProfilerColors.CPU_CALLCHART_APP, ProfilerColors.CPU_FLAMECHART_APP,
                           ProfilerColors.CPU_CALLCHART_APP_HOVER, ProfilerColors.CPU_FLAMECHART_APP_HOVER)
  }

  @Test
  fun testNativeColors() {
    val invalidModel = JavaMethodModel("toString", "com.example.MyClass")
    try {
      NativeModelHChartColors.getFillColor(invalidModel, CaptureModel.Details.Type.CALL_CHART, false, false)
      fail()
    }
    catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Model must be a subclass of NativeNodeModel.")
    }

    val vendorModel = CppFunctionModel.Builder("Load").setIsUserCode(false).setClassOrNamespace("glClear").build()
    doTestNativeColors(vendorModel, ProfilerColors.CPU_CALLCHART_VENDOR, ProfilerColors.CPU_FLAMECHART_VENDOR,
                       ProfilerColors.CPU_CALLCHART_VENDOR_HOVER, ProfilerColors.CPU_FLAMECHART_VENDOR_HOVER)

    val platformModel = CppFunctionModel.Builder("Inflate").setIsUserCode(false).setClassOrNamespace("android::Activity").build()
    doTestNativeColors(platformModel, ProfilerColors.CPU_CALLCHART_PLATFORM, ProfilerColors.CPU_FLAMECHART_PLATFORM,
                       ProfilerColors.CPU_CALLCHART_PLATFORM_HOVER, ProfilerColors.CPU_FLAMECHART_PLATFORM_HOVER)

    val appModel = CppFunctionModel.Builder("DoFrame").setIsUserCode(true).setClassOrNamespace("PlayScene").build()
    doTestNativeColors(appModel, ProfilerColors.CPU_CALLCHART_APP, ProfilerColors.CPU_FLAMECHART_APP,
                       ProfilerColors.CPU_CALLCHART_APP_HOVER, ProfilerColors.CPU_FLAMECHART_APP_HOVER)
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

  @Test
  fun testFittingTextForMalformedMethod() {
    // The name "..B1.1" was returned from simpleperf when profiling a library that linked against
    // libm.so (the c math library)
    checkFittingText(nodeModel = JavaMethodModel("1", "..B1"), expectedTexts = listOf(
      "..B1.1",
      "..B.1",
      "1"
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
      renderer.render(graphics, node, Rectangle2D.Float(), Rectangle2D.Float(), false)
      assertThat(graphics.drawnString).isEqualTo(text)
      prevTextLength = text.length
    }
  }

  private fun doTestJavaMethodColors(model: JavaMethodModel,
                                     callChartFill: Color,
                                     flameChartFill: Color,
                                     callChartFillHover: Color,
                                     flameChartFillHover: Color) {
    val callChart = CaptureModel.Details.Type.CALL_CHART
    val flameChart = CaptureModel.Details.Type.FLAME_CHART

    // Call chart not unmatched
    var color = JavaMethodHChartColors.getFillColor(model, callChart, false, false)
    assertThat(color).isEqualTo(callChartFill)
    // Call chart unmatched
    color = JavaMethodHChartColors.getFillColor(model, callChart, true, false)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(callChartFill))
    // Call chart hover
    color = JavaMethodHChartColors.getFillColor(model, callChart, false, true)
    assertThat(color).isEqualTo(callChartFillHover)
    // Flame chart not unmatched
    color = JavaMethodHChartColors.getFillColor(model, flameChart, false, false)
    assertThat(color).isEqualTo(flameChartFill)
    // Flame chart unmatched
    color = JavaMethodHChartColors.getFillColor(model, flameChart, true, false)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(flameChartFill))
    // Flame chart hover
    color = JavaMethodHChartColors.getFillColor(model, flameChart, false, true)
    assertThat(color).isEqualTo(flameChartFillHover)
  }

  private fun doTestNativeColors(model: NativeNodeModel, callChartFill: Color,
                                 flameChartFill: Color,
                                 callChartFillHover: Color,
                                 flameChartFillHover: Color) {
    val callChart = CaptureModel.Details.Type.CALL_CHART
    val flameChart = CaptureModel.Details.Type.FLAME_CHART

    // Call chart not unmatched
    var color = NativeModelHChartColors.getFillColor(model, callChart, false, false)
    assertThat(color).isEqualTo(callChartFill)
    // Call chart unmatched
    color = NativeModelHChartColors.getFillColor(model, callChart, true, false)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(callChartFill))
    // Call chart hover
    color = NativeModelHChartColors.getFillColor(model, callChart, false, true)
    assertThat(color).isEqualTo(callChartFillHover)
    // Flame chart not unmatched
    color = NativeModelHChartColors.getFillColor(model, flameChart, false, false)
    assertThat(color).isEqualTo(flameChartFill)
    // Flame chart unmatched
    color = NativeModelHChartColors.getFillColor(model, flameChart, true, false)
    assertThat(color).isEqualTo(CaptureNodeHRenderer.toUnmatchColor(flameChartFill))
    // Flame chart hover
    color = NativeModelHChartColors.getFillColor(model, flameChart, false, true)
    assertThat(color).isEqualTo(flameChartFillHover)
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
