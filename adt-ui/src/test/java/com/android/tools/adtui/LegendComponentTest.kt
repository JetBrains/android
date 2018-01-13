/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.tools.adtui.LegendComponent.IconInstruction
import com.android.tools.adtui.LegendComponent.Orientation
import com.android.tools.adtui.instructions.GapInstruction
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.RenderInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.model.legend.Legend
import com.android.tools.adtui.model.legend.LegendComponentModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Color
import java.util.concurrent.TimeUnit

class LegendComponentTest {
  @Test
  fun addingAndRemovingLegendsCreatesTextInstructions() {
    val model = LegendComponentModel(0)
    val legend1 = FakeLegend("Name1")
    val legend2 = FakeLegend("Name2")
    val legend3 = FakeLegend("Name3", "Value3")
    val legendComponent = LegendComponent.Builder(model).setVerticalPadding(0).setHorizontalPadding(0).build()

    assertThat(legendComponent.instructions.size).isEqualTo(0)
    assertText(legendComponent, listOf())
    assertThat(legendComponent.preferredSize.width).isEqualTo(0)
    assertThat(legendComponent.preferredSize.height).isEqualTo(0)

    model.add(legend1)
    model.update(TimeUnit.SECONDS.toNanos(1))

    assertText(legendComponent, listOf())
    assertThat(legendComponent.preferredSize.width).isEqualTo(0)
    assertThat(legendComponent.preferredSize.height).isEqualTo(0)

    model.add(legend2)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertText(legendComponent, listOf())

    model.add(legend3)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.preferredSize.width).isGreaterThan(0)
    assertThat(legendComponent.preferredSize.height).isGreaterThan(0)
    assertText(legendComponent, listOf("Name3: ", "Value3"))

    legend1.setValue("Value1")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertText(legendComponent, listOf("Name1: ", "Value1", "Name3: ", "Value3"))

    model.remove(legend1)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertText(legendComponent, listOf("Name3: ", "Value3"))

    model.remove(legend3)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertText(legendComponent, listOf())

    model.remove(legend2)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.size).isEqualTo(0) // Empty legend
    assertText(legendComponent, listOf())
    assertThat(legendComponent.preferredSize.width).isEqualTo(0)
    assertThat(legendComponent.preferredSize.height).isEqualTo(0)
  }

  @Test
  fun addingLegendsWithIconsCreatesIconInstructions() {
    val model = LegendComponentModel(0)
    val legend1 = FakeLegend("Test1", "Value1")
    val legend2 = FakeLegend("Test2", "Value2")
    val legend3 = FakeLegend("Test3", "Value3")
    val config2 = LegendConfig(LegendConfig.IconType.BOX, Color.GREEN)
    val config3 = LegendConfig(LegendConfig.IconType.LINE, Color.BLUE)

    val legendComponent = LegendComponent.Builder(model).setVerticalPadding(0).setHorizontalPadding(0).build()
    legendComponent.configure(legend2, config2)
    legendComponent.configure(legend3, config3)

    assertThat(legendComponent.instructions.size).isEqualTo(0)
    assertIcons(legendComponent, listOf())

    model.add(legend1)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertIcons(legendComponent, listOf())

    model.add(legend2)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertIcons(legendComponent, listOf(LegendConfig.IconType.BOX))

    model.add(legend3)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertIcons(legendComponent, listOf(LegendConfig.IconType.BOX, LegendConfig.IconType.LINE))
  }

  @Test
  fun verticalLegendsAddNewRowInstructions() {
    val model = LegendComponentModel(0)
    val legend1 = FakeLegend("Test1", "Value1")
    val legend2 = FakeLegend("Test2", "Value2")
    val legend3 = FakeLegend("Test3", "Value3")

    val legendComponent = LegendComponent.Builder(model).setOrientation(LegendComponent.Orientation.VERTICAL).build()

    assertThat(legendComponent.instructions.size).isEqualTo(0)

    model.add(legend1)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is NewRowInstruction }).isEqualTo(0)

    model.add(legend2)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is NewRowInstruction }).isEqualTo(1)

    model.add(legend3)
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is NewRowInstruction }).isEqualTo(2)
  }

  @Test
  fun paddingIsRespected() {
    val model = LegendComponentModel(0)

    val CRAZY_LARGE_X_PADDING = 123
    val CRAZY_LARGE_Y_PADDING = 321
    val legendComponent = LegendComponent.Builder(model).
        setHorizontalPadding(CRAZY_LARGE_X_PADDING).
        setVerticalPadding(CRAZY_LARGE_Y_PADDING).
        build()

    assertThat(legendComponent.preferredSize.width).isEqualTo(CRAZY_LARGE_X_PADDING * 2)
    assertThat(legendComponent.preferredSize.height).isEqualTo(CRAZY_LARGE_Y_PADDING * 2)
  }

  @Test
  fun horizontalLegendsAddGapsToKeepLegendsAtTheirMaxSize() {
    val model = LegendComponentModel(0)
    val legend1 = FakeLegend("Test1", "0 b/s")
    val legend2 = FakeLegend("Test2", "0 b/s")
    model.add(legend1)
    model.add(legend2)

    val legendComponent = LegendComponent(model)

    // Horizontal legend adds gaps between entries. We don't want to count those later in the test.
    val gapBaseline = legendComponent.instructions.count { it is GapInstruction }

    // Long text makes for a new max size
    legend1.setValue("12321 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline)

    // Gap needed to mantain max size
    legend1.setValue("0 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 1)

    // Gap still needed to mantain max size
    legend1.setValue("123 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 1)

    // Long text makes for a new max size (in legend 2)
    legend2.setValue("9001 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 1)

    // Now both legends have gaps
    legend2.setValue("0 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 2)

    // Gap no longer needed in legend 1
    legend1.setValue("123456789 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 1)
  }

  @Test
  fun verticalLegendsDontAddGapsToKeepLegendsAtTheirMaxSize() {
    val model = LegendComponentModel(0)
    val legend = FakeLegend("Test1", "0 b/s")
    model.add(legend)

    val legendComponent = LegendComponent.Builder(model).setOrientation(Orientation.VERTICAL).build()

    // Horizontal legend adds gaps between entries. We don't want to count those later in the test.
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(0)

    // Increase text to max size
    legend.setValue("12321 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(0)

    // Shrink text again. In vertical legends, text will be allowed to shrink (no gap added)
    legend.setValue("0 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(0)
  }

  @Test
  fun gapsInHorizontalLegendsKeepMaxSizeAsExpected() {
    val model = LegendComponentModel(0)
    val legend = FakeLegend("Test1", "0 b/s")
    model.add(legend)

    val legendComponent = LegendComponent(model)

    val initialWidth = legendComponent.preferredSize.width

    // Increase max size
    legend.setValue("12321 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))

    var currWidth = legendComponent.preferredSize.width
    assertThat(currWidth).isGreaterThan(initialWidth)

    val prevWidth = currWidth

    // Shrinking value text doesn't affect max size
    legend.setValue("0 b/s")
    model.update(TimeUnit.SECONDS.toNanos(1))

    currWidth = legendComponent.preferredSize.width
    assertThat(currWidth).isEqualTo(prevWidth)
  }

  @Test
  fun gapAndIconTotalWidthForVerticalLegendsKeepTheSame() {
    val model = LegendComponentModel(0)
    val legendComponent = LegendComponent.Builder(model).setOrientation(Orientation.VERTICAL).build()
    for (type in LegendConfig.IconType.values()) {
      if (type != LegendConfig.IconType.NONE) {
        val legend = FakeLegend(type.name, "1 b/s")
        val legendConfig = LegendConfig(type, Color.BLUE)
        legendComponent.configure(legend, legendConfig)
        model.add(legend)
      }
    }
    model.update(TimeUnit.SECONDS.toNanos(1))

    val instructions = legendComponent.instructions
    val first = instructions.first { it is IconInstruction }
    val getNext = { item: RenderInstruction -> instructions.get(instructions.indexOf(item) + 1) }
    instructions.filterIsInstance<IconInstruction>().forEach {
      assertThat(it.size.width + getNext(it).size.width).isEqualTo(first.size.width + getNext(first).size.width)
    }
  }

  private fun assertText(legend: LegendComponent, text: List<String>) {
    assertThat(legend.instructions.filterIsInstance<TextInstruction>().map { it.text })
        .containsExactlyElementsIn(text).inOrder()
  }

  private fun assertIcons(legend: LegendComponent, icons: List<LegendConfig.IconType>) {
    assertThat(legend.instructions.filterIsInstance<IconInstruction>().map { it.myType })
        .containsExactlyElementsIn(icons).inOrder()
  }

  private class FakeLegend(name: String, value: String? = null) : Legend {
    // Need to use backing fields, since otherwise "name" and "value" conflict in Kotlin with the
    // getter functions "getName" and "getValue"
    private val _name = name
    private var _value = value
    override fun getName(): String = _name
    override fun getValue(): String? = _value
    fun setValue(value: String) {
      _value = value
    }
  }
}
