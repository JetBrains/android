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

import com.android.tools.adtui.LegendComponent.LegendIconInstruction
import com.android.tools.adtui.LegendComponent.Orientation
import com.android.tools.adtui.instructions.GapInstruction
import com.android.tools.adtui.instructions.IconInstruction
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.RenderInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.legend.Legend
import com.android.tools.adtui.model.legend.LegendComponentModel
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import org.junit.Test
import java.awt.Color

class LegendComponentTest {
  @Test
  fun addingAndRemovingLegendsCreatesTextInstructions() {
    val range = Range(0.0, 0.0) // Just a valid starting range, no special meaning to 0.0.
    val model = LegendComponentModel(range)
    val legend1 = FakeLegend("Name1")
    val legend2 = FakeLegend("Name2")
    val legend3 = FakeLegend("Name3", "Value3")
    val legendComponent = LegendComponent.Builder(model).setVerticalPadding(0).setHorizontalPadding(0).build()
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    assertThat(legendComponent.instructions.size).isEqualTo(0)
    assertText(legendComponent, listOf())
    assertThat(legendComponent.preferredSize.width).isEqualTo(0)
    assertThat(legendComponent.preferredSize.height).isEqualTo(0)

    model.add(legend1)
    modifyRange(range) // Some valid range that is different from what it was before (0.0, 0.0).

    assertText(legendComponent, listOf())
    assertThat(legendComponent.preferredSize.width).isEqualTo(0)
    assertThat(legendComponent.preferredSize.height).isEqualTo(0)

    model.add(legend2)
    modifyRange(range)
    assertText(legendComponent, listOf())

    model.add(legend3)
    modifyRange(range)
    assertThat(legendComponent.preferredSize.width).isGreaterThan(0)
    assertThat(legendComponent.preferredSize.height).isGreaterThan(0)
    assertText(legendComponent, listOf("Name3: ", "Value3"))

    legend1.setValue("Value1")
    modifyRange(range)
    assertText(legendComponent, listOf("Name1: ", "Value1", "Name3: ", "Value3"))

    model.remove(legend1)
    modifyRange(range)
    assertText(legendComponent, listOf("Name3: ", "Value3"))

    model.remove(legend3)
    modifyRange(range)
    assertText(legendComponent, listOf())

    model.remove(legend2)
    modifyRange(range)
    assertThat(legendComponent.instructions.size).isEqualTo(0) // Empty legend
    assertText(legendComponent, listOf())
    assertThat(legendComponent.preferredSize.width).isEqualTo(0)
    assertThat(legendComponent.preferredSize.height).isEqualTo(0)
  }

  @Test
  fun addingLegendsWithIconsCreatesIconInstructions() {
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    val legend1 = FakeLegend("Test1", "Value1")
    val legend2 = FakeLegend("Test2", "Value2")
    val legend3 = FakeLegend("Test3", "Value3")
    val legend4 = FakeLegend("Test4", "Value4")
    val config2 = LegendConfig(LegendConfig.IconType.BOX, Color.GREEN)
    val config3 = LegendConfig(LegendConfig.IconType.LINE, Color.BLUE)
    val icon = AllIcons.General.Add
    val croppedIcon = LegendComponent.cropAndCacheIcon(icon)
    val config4 = LegendConfig({ s -> if ("Value4" == s) icon else null }, Color.RED)

    val legendComponent = LegendComponent.Builder(model).setVerticalPadding(0).setHorizontalPadding(0).build()
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()
    legendComponent.configure(legend2, config2)
    legendComponent.configure(legend3, config3)
    legendComponent.configure(legend4, config4)

    assertThat(legendComponent.instructions.size).isEqualTo(0)
    assertIcons(legendComponent, listOf())

    model.add(legend1)
    modifyRange(range)
    assertIcons(legendComponent, listOf())

    model.add(legend2)
    modifyRange(range)
    assertIcons(legendComponent, listOf(LegendConfig.IconType.BOX))

    model.add(legend3)
    modifyRange(range)
    assertIcons(legendComponent, listOf(LegendConfig.IconType.BOX, LegendConfig.IconType.LINE))

    model.add(legend4)
    modifyRange(range)
    assertThat(legendComponent.instructions.filterIsInstance<IconInstruction>().map { it.icon }).containsExactly(croppedIcon)
  }

  @Test
  fun verticalLegendsAddNewRowInstructions() {
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    val legend1 = FakeLegend("Test1", "Value1")
    val legend2 = FakeLegend("Test2", "Value2")
    val legend3 = FakeLegend("Test3", "Value3")

    val legendComponent = LegendComponent.Builder(model).setOrientation(LegendComponent.Orientation.VERTICAL).build()
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    assertThat(legendComponent.instructions.size).isEqualTo(0)

    model.add(legend1)
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is NewRowInstruction }).isEqualTo(0)

    model.add(legend2)
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is NewRowInstruction }).isEqualTo(1)

    model.add(legend3)
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is NewRowInstruction }).isEqualTo(2)
  }

  @Test
  fun paddingIsRespected() {
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)

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
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    val legend1 = FakeLegend("Test1", "0 b/s")
    val legend2 = FakeLegend("Test2", "0 b/s")
    model.add(legend1)
    model.add(legend2)

    val legendComponent = LegendComponent(model)
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    // Horizontal legend adds gaps between entries. We don't want to count those later in the test.
    modifyRange(range)
    val gapBaseline = legendComponent.instructions.count { it is GapInstruction }

    // Long text makes for a new max size
    legend1.setValue("12321 b/s")
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline)

    // Gap needed to maintain max size
    legend1.setValue("0 b/s")
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 1)

    // Gap still needed to maintain max size
    legend1.setValue("123 b/s")
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 1)

    // Long text makes for a new max size (in legend 2)
    legend2.setValue("9001 b/s")
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 1)

    // Now both legends have gaps
    legend2.setValue("0 b/s")
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 2)

    // Gap no longer needed in legend 1
    legend1.setValue("123456789 b/s")
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(gapBaseline + 1)
  }

  @Test
  fun verticalLegendsDontAddGapsToKeepLegendsAtTheirMaxSize() {
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    val legend = FakeLegend("Test1", "0 b/s")
    model.add(legend)

    val legendComponent = LegendComponent.Builder(model).setOrientation(Orientation.VERTICAL).build()
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    // Horizontal legend adds gaps between entries. We don't want to count those later in the test.
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(0)

    // Increase text to max size
    legend.setValue("12321 b/s")
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(0)

    // Shrink text again. In vertical legends, text will be allowed to shrink (no gap added)
    legend.setValue("0 b/s")
    modifyRange(range)
    assertThat(legendComponent.instructions.count { it is GapInstruction }).isEqualTo(0)
  }

  @Test
  fun gapsInHorizontalLegendsKeepMaxSizeAsExpected() {
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    val legend = FakeLegend("Test1", "0 b/s")
    model.add(legend)

    val legendComponent = LegendComponent(model)
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    val initialWidth = legendComponent.preferredSize.width

    // Increase max size
    legend.setValue("12321 b/s")
    modifyRange(range)

    var currWidth = legendComponent.preferredSize.width
    assertThat(currWidth).isGreaterThan(initialWidth)

    val prevWidth = currWidth

    // Shrinking value text doesn't affect max size
    legend.setValue("0 b/s")
    modifyRange(range)

    currWidth = legendComponent.preferredSize.width
    assertThat(currWidth).isEqualTo(prevWidth)
  }

  @Test
  fun gapAndIconTotalWidthForVerticalLegendsKeepTheSame() {
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    val legendComponent = LegendComponent.Builder(model).setOrientation(Orientation.VERTICAL).build()
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    for (type in LegendConfig.IconType.values()) {
      if (type != LegendConfig.IconType.NONE) {
        val legend = FakeLegend(type.name, "1 b/s")
        val legendConfig =
          if (type == LegendConfig.IconType.CUSTOM)
            LegendConfig({ _ -> AllIcons.General.Add }, Color.RED)
          else
            LegendConfig(type, Color.BLUE)
        legendComponent.configure(legend, legendConfig)
        model.add(legend)
      }
    }
    modifyRange(range)

    val instructions = legendComponent.instructions
    val first = instructions.first { it is LegendIconInstruction }
    val getNext = { item: RenderInstruction -> instructions.get(instructions.indexOf(item) + 1) }
    instructions.filterIsInstance<LegendIconInstruction>().forEach {
      assertThat(it.size.width + getNext(it).size.width).isEqualTo(first.size.width + getNext(first).size.width)
    }
  }

  @Test
  fun testSwitchingLegendComponentModelDoesNotResultInNA() {
    val name = "test"
    val value = "1 b/s"

    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    model.add(FakeLegend("test", "1 b/s"))
    var legendComponent = LegendComponent(model)
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    modifyRange(range)
    assertText(legendComponent, listOf("$name: ", value))

    legendComponent = LegendComponent(model)
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    modifyRange(range)
    assertText(legendComponent, listOf("$name: ", value))
  }

  @Test
  fun componentNotUpdatedIfNotShowing() {
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    val legend = FakeLegend("Name", "Value")
    val legendComponent = LegendComponent.Builder(model).setVerticalPadding(0).setHorizontalPadding(0).build()

    model.add(legend)
    modifyRange(range) // Some valid range that is different from what it was before (0.0, 0.0).

    assertText(legendComponent, listOf())
    assertThat(legendComponent.preferredSize.width).isEqualTo(0)
    assertThat(legendComponent.preferredSize.height).isEqualTo(0)
  }

  @Test
  fun noTextInstructionForEmptyName() {
    val range = Range(0.0, 0.0)
    val model = LegendComponentModel(range)
    val legendComponent = LegendComponent.Builder(model).build()
    // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
    // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
    legendComponent.addNotify()

    // If name is empty (e.g. icon legend), we should not create an TextInstruction for it.
    val legend = FakeLegend("", "Value")
    model.add(legend)
    modifyRange(range) // Some valid range that is different from what it was before (0.0, 0.0).

    assertThat(legendComponent.instructions.size).isEqualTo(1)
    assertText(legendComponent, listOf("Value"))
  }

  @Test
  fun `legend not showing values is smaller`() {
    val model = LegendComponentModel(Range(0.0, 0.0))
    val legends = makeSimpleLegends(3)

    fun make(showValues: Boolean) = LegendComponent.Builder(model)
      .setShowValues(showValues)
      .build().apply {
        // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
        // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
        addNotify()
      }

    val fullLegend = make(true)
    val compactLegend = make(false)
    legends.forEach(model::add)

    assertThat(compactLegend.instructions.size).isLessThan(fullLegend.instructions.size)
    assertThat(compactLegend.preferredSize.width).isLessThan(fullLegend.preferredSize.width)
  }

  @Test
  fun `legend with excluded name is smaller`() {
    val model = LegendComponentModel(Range(0.0, 0.0))
    val legends = makeSimpleLegends(3)

    fun make(vararg excluded: String) = LegendComponent.Builder(model)
      .setExcludedLegends(*excluded)
      .build().apply {
        // Hack to force legendComponent's isShowing() to return true. The LegendComponent always early-return during an update if
        // isShowing() == false, as an optimization to reduce the number of queries made to the dataseries/datastore.
        addNotify()
      }

    val fullLegend = make() // full legend contains Test1, Test2, Test3
    val partLegend = make("Test2")
    legends.forEach(model::add)

    assertThat(partLegend.instructions.size).isLessThan(fullLegend.instructions.size)
    assertThat(partLegend.preferredSize.width).isLessThan(fullLegend.preferredSize.width)
  }

  private fun makeSimpleLegends(n: Int) = (1 .. n).map { FakeLegend("Test$it", "Value$it") }

  private fun assertText(legend: LegendComponent, text: List<String>) {
    assertThat(legend.instructions.filterIsInstance<TextInstruction>().map { it.text })
      .containsExactlyElementsIn(text).inOrder()
  }

  private fun assertIcons(legend: LegendComponent, icons: List<LegendConfig.IconType>) {
    assertThat(legend.instructions.filterIsInstance<LegendIconInstruction>().map { it.myType })
      .containsExactlyElementsIn(icons).inOrder()
  }

  // Modifies the Range such that it will change the Range.
  private fun modifyRange(range: Range) {
    range.set(range.min + 0.1, range.max + 0.1)
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
