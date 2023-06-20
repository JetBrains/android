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
package com.android.tools.profilers.memory.chart

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.DataVisualizationColors
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryCaptureObjectTestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.ColorUtil
import com.intellij.ui.Graphics2DDelegate
import com.intellij.util.ui.ImageUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Font
import java.awt.Paint
import java.awt.Shape
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

class HeapSetNodeHRendererTest {
  private val timer = FakeTimer()

  @get:Rule
  var grpcChannel = FakeGrpcChannel("MEMORY_TEST_CHANNEL", FakeTransportService(timer))
  private lateinit var stage: MainMemoryProfilerStage

  @Before
  fun setup() {
    val loader = FakeCaptureObjectLoader()
    loader.setReturnImmediateFuture(true)
    val fakeIdeProfilerServices = FakeIdeProfilerServices()
    stage = MainMemoryProfilerStage(
      StudioProfilers(ProfilerClient(grpcChannel.channel), fakeIdeProfilerServices, FakeTimer()),
      loader)
  }

  @Test
  fun defaultRenderProperties() {
    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    val model = MemoryVisualizationModel()
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    assertThat(model.isSizeAxis()).isFalse()
    assertThat(model.formatter()).isInstanceOf(SingleUnitAxisFormatter::class.java)
    val simpleNode = ClassifierSetHNode(model, heapSet, 0)
    val name = heapSet.name
    val renderer = HeapSetNodeHRenderer()
    val renderWindow = Rectangle2D.Float(0.0f, 0.0f, 1000.0f, 10.0f)
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, renderWindow, isFocused = false, isDeselected = false)

    assertThat(fakeGraphics.fillShapes).hasSize(1)
    fakeGraphics.expectFillShapes(renderWindow)

    assertThat(fakeGraphics.colors).hasSize(2)
    assertThat(fakeGraphics.colors).containsExactly(
      DataVisualizationColors.paletteManager.getBackgroundColor(name.hashCode()),
      DataVisualizationColors.paletteManager.getForegroundColor(name.hashCode()))

    assertThat(fakeGraphics.fonts).hasSize(1)
    assertThat(fakeGraphics.fonts).containsExactly(fakeGraphics.font)

    assertThat(fakeGraphics.strings).hasSize(1)
    assertThat(fakeGraphics.strings).containsExactly(name)
  }

  @Test
  fun deselectedState() {
    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    val model = MemoryVisualizationModel()
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    val simpleNode = ClassifierSetHNode(model, heapSet, 0)
    val name = heapSet.name
    val renderer = HeapSetNodeHRenderer()
    val renderWindow = Rectangle2D.Float(0.0f, 0.0f, 1000.0f, 10.0f)
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, renderWindow, isFocused = false, isDeselected = true)
    assertThat(fakeGraphics.colors).hasSize(2)
    assertThat(fakeGraphics.colors).containsExactly(
      DataVisualizationColors.paletteManager.toGrayscale(DataVisualizationColors.paletteManager.getBackgroundColor(name.hashCode())),
      DataVisualizationColors.paletteManager.getForegroundColor(name.hashCode()))
  }

  @Test
  fun filteredState() {
    val heapSet = Mockito.spy(MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage))
    whenever(heapSet.isFiltered).thenReturn(true)
    val model = MemoryVisualizationModel()
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    val simpleNode = ClassifierSetHNode(model, heapSet, 0)
    val name = heapSet.name
    val renderer = HeapSetNodeHRenderer()
    val renderWindow = Rectangle2D.Float(0.0f, 0.0f, 1000.0f, 10.0f)
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, renderWindow, isFocused = false, isDeselected = false)
    assertThat(fakeGraphics.colors).hasSize(3)
    assertThat(fakeGraphics.colors).containsExactly(
      DataVisualizationColors.paletteManager.getBackgroundColor(name.hashCode()),
      DataVisualizationColors.paletteManager.getForegroundColor(name.hashCode()),
      ColorUtil.withAlpha(DataVisualizationColors.paletteManager.getForegroundColor(name.hashCode()), .2))
  }

  @Test
  fun matchedState() {
    val heapSet = Mockito.spy(MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage))
    whenever(heapSet.isFiltered).thenReturn(true)
    whenever(heapSet.isMatched).thenReturn(true)
    val model = MemoryVisualizationModel()
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    val simpleNode = ClassifierSetHNode(model, heapSet, 0)
    val renderer = HeapSetNodeHRenderer()
    val renderWindow = Rectangle2D.Float(0.0f, 0.0f, 1000.0f, 10.0f)
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, renderWindow, isFocused = false, isDeselected = true)
    assertThat(fakeGraphics.fonts).hasSize(2)
    assertThat(fakeGraphics.fonts).containsExactly(fakeGraphics.font, fakeGraphics.font.deriveFont(Font.BOLD))
  }

  @Test
  fun drawingAreaTooSmallForText() {
    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    val model = MemoryVisualizationModel()
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    val simpleNode = ClassifierSetHNode(model, heapSet, 0)
    val renderer = HeapSetNodeHRenderer()
    val renderWindow = Rectangle2D.Float(0.0f, 0.0f, 1.0f, 10.0f)
    val fakeGraphics = TestGraphics2D()
    renderer.render(fakeGraphics, simpleNode, renderWindow, renderWindow, isFocused = false, isDeselected = true)

    // Expect no drawString call.
    assertThat(fakeGraphics.strings).isEmpty()
  }

  private class TestGraphics2D : Graphics2DDelegate(ImageUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()) {
    val fillShapes = mutableListOf<Shape>()
    val colors = mutableListOf<Paint?>()
    val strings = mutableListOf<String>()
    val fonts = mutableListOf<Font?>()

    override fun fill(s: Shape) {
      fillShapes.add(s)
    }

    override fun setPaint(paint: Paint?) {
      colors.add(paint)
    }

    override fun drawString(str: String, x: Float, y: Float) {
      strings.add(str)
    }

    override fun setFont(font: Font?) {
      fonts.add(font)
    }

    fun expectFillShapes(vararg rectangles: Rectangle2D) {
      for (i in rectangles.indices) {
        val boundsA = fillShapes[i].bounds2D
        val boundsB = rectangles[i]
        assertThat(boundsA.y).isWithin(0.0001).of(boundsB.y)
        assertThat(boundsA.x).isWithin(0.0001).of(boundsB.x)
        assertThat(boundsA.width).isWithin(0.0001).of(boundsB.width)
        assertThat(boundsA.height).isWithin(0.0001).of(boundsB.height)
      }
    }
  }
}