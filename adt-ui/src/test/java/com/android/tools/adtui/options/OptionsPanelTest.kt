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
package com.android.tools.adtui.options

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.options.OptionsProperty
import com.android.tools.adtui.model.options.OptionsProvider
import com.android.tools.adtui.model.options.Slider
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.ui.options.OptionsPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.JSpinner

@RunsInEdt
class OptionsPanelTest {
  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun defaultBindings() {
    val panel = OptionsPanel()
    assertThat(panel.binders).containsKey(Int::class.java)
    assertThat(panel.binders).containsKey(Boolean::class.java)
    assertThat(panel.binders).containsKey(String::class.java)
  }

  @Test
  fun boolBinding() {
    val panel = OptionsPanel()
    val provider = BoolBindingProvider()
    val walker = TreeWalker(panel)
    panel.setOption(provider, false, false)
    val checkBoxList = walker.descendants().filterIsInstance(JBCheckBox::class.java)
    assertThat(checkBoxList).hasSize(2)
    assertThat(checkBoxList[0].text).isEqualTo("Name1")
    assertThat(checkBoxList[1].text).isEqualTo("Name2")
    assertThat(checkBoxList[0].isSelected).isEqualTo(provider.boolTestOne)
    checkBoxList[0].isSelected = !checkBoxList[0].isSelected
    assertThat(checkBoxList[0].isSelected).isEqualTo(provider.boolTestOne)
    // Test readonly
    panel.setOption(provider, true, false)
    val readOnlyCheckboxes = walker.descendants().filterIsInstance(JBCheckBox::class.java)
    assertThat(readOnlyCheckboxes).hasSize(2)
    assertThat(readOnlyCheckboxes[0].isEnabled).isEqualTo(false)
    assertThat(readOnlyCheckboxes[1].isEnabled).isEqualTo(false)
  }

  @Test
  fun taskBasedUxHasHeader() {
    val panel = OptionsPanel()
    val provider = BoolBindingProvider()
    val walker = TreeWalker(panel)
    panel.setOption(provider, false, true)
    val headerLabel = walker.descendants().filterIsInstance(JLabel::class.java).filter { ((it.text)) == "testName" }
    // Header is present in taskBasedUx
    assertThat(headerLabel.size).isEqualTo(1)
  }

  @Test
  fun taskBasedUxHasNoHeaderIfNameIsNotThereInProvider() {
    val panel = OptionsPanel()
    val provider = IntBindingProvider()
    val walker = TreeWalker(panel)
    panel.setOption(provider, false, true)
    val headerLabel = walker.descendants().filterIsInstance(JLabel::class.java).filter { ((it.text)) == "testName" }
    // Header not present in taskBasedUx since it doesn't have name property
    assertThat(headerLabel.size).isEqualTo(0)
  }

  @Test
  fun intBinding() {
    val panel = OptionsPanel()
    val provider = IntBindingProvider()
    val walker = TreeWalker(panel)
    panel.setOption(provider, false, false)
    val labels = walker.descendants().filterIsInstance(JLabel::class.java)
    assertThat(labels).hasSize(4) // Group, Name,  Description, Unit
    assertThat(labels[0].text).isEqualTo("Desc")
    assertThat(labels[1].text).isEqualTo("Int")
    assertThat(labels[2].text).isEqualTo("Name1")
    assertThat(labels[3].text).isEqualTo("Unit")
    val spinners = walker.descendants().filterIsInstance(JSpinner::class.java)
    assertThat(spinners).hasSize(1)
    assertThat(spinners[0].value).isEqualTo(provider.intTestOne)
    spinners[0].value = 200
    assertThat(spinners[0].value).isEqualTo(provider.intTestOne)
    // Test readonly
    panel.setOption(provider, true, false)
    val readOnlySpinner = walker.descendants().filterIsInstance(JSpinner::class.java)
    assertThat(readOnlySpinner).hasSize(1)
    assertThat(readOnlySpinner[0].isEnabled).isEqualTo(false)
  }

  @Test
  fun stringBinding() {
    val panel = OptionsPanel()
    val provider = StringBindingProvider()
    val walker = TreeWalker(panel)
    panel.setOption(provider, false, false)
    val labels = walker.descendants().filterIsInstance(JLabel::class.java)
    assertThat(labels).hasSize(3) // Name Group Description
    val fields = walker.descendants().filterIsInstance(JBTextField::class.java)
    assertThat(fields).hasSize(1)
    assertThat(fields[0].text).isEqualTo(provider.stringTestOne)
    FakeUi(fields[0]).keyboard.pressAndRelease(0)
    assertThat(fields[0].text).isEqualTo(provider.stringTestOne)
    // Test readonly
    panel.setOption(provider, true, false)
    val readOnlyFields = walker.descendants().filterIsInstance(JBTextField::class.java)
    assertThat(readOnlyFields).hasSize(1)
    assertThat(readOnlyFields[0].isEnabled).isEqualTo(false)

    //Test taskBaseUx has no groupName but has other fields
    panel.setOption(provider, false, true)
    val labels1 = walker.descendants().filterIsInstance(JLabel::class.java)
    assertThat(labels1).hasSize(2)
    val fields1 = walker.descendants().filterIsInstance(JBTextField::class.java)
    assertThat(fields1).hasSize(1)
    assertThat(fields1[0].text).isEqualTo(provider.stringTestOne)
    FakeUi(fields1[0]).keyboard.pressAndRelease(0)
    assertThat(fields1[0].text).isEqualTo(provider.stringTestOne)
  }

  @Test
  fun nullBindingTest() {
    val panel = OptionsPanel()
    val provider = StringBindingProvider()
    val walker = TreeWalker(panel)
    panel.setOption(provider, false, false)
    panel.setOption(null, false, false)
    assertThat(walker.descendants()).hasSize(1) // Root panel
  }

  @Test
  fun sliderTest() {
    val panel = OptionsPanel()
    val provider = SliderBindingProvider()
    val walker = TreeWalker(panel)
    panel.setOption(provider, false, false)
    val labels = walker.descendants().filterIsInstance(JLabel::class.java)
    assertThat(labels).hasSize(4) // Group, Name,  Description, Unit
    assertThat(labels[0].text).isEqualTo("Desc")
    assertThat(labels[1].text).isEqualTo("Slider")
    assertThat(labels[2].text).isEqualTo("Name1")
    assertThat(labels[3].text).isEqualTo("100 Unit")
    var slider = walker.descendants().filterIsInstance(JSlider::class.java)[0]
    assertThat(slider.minimum).isEqualTo(0)
    assertThat(slider.maximum).isEqualTo(100)
    assertThat(slider.value).isEqualTo(provider.sliderTest)
    slider.value = 50
    assertThat(slider.value).isEqualTo(provider.sliderTest)
    assertThat(slider.isEnabled).isEqualTo(true)
    // Test readonly
    panel.setOption(provider, true, false)
    slider = walker.descendants().filterIsInstance(JSlider::class.java)[0]
    assertThat(slider.isEnabled).isEqualTo(false)
  }

  @Test
  fun unknownBindingTest() {
    val panel = OptionsPanel()
    val provider = UnknownBindingProvider()
    val walker = TreeWalker(panel)
    panel.setOption(provider, false, false)
    val labels = walker.descendants().filterIsInstance(JLabel::class.java)
    assertThat(labels).hasSize(1) // Group, Name,  Description, Unit
    assertThat(labels[0].text).isEqualTo("Unknown return type (${BoolBindingProvider::class.java.name}) for property \"other\"")
  }
}

class UnknownBindingProvider: OptionsProvider {
  @OptionsProperty()
  var other = BoolBindingProvider()
}

class BoolBindingProvider : OptionsProvider {
  @OptionsProperty(name = "Name1", group = "Bool", description = "Desc", order = 0)
  var boolTestOne = true
  @OptionsProperty(name = "Name2", group = "Bool", description = "Desc", order = 1)
  var boolTestTwo = true
  @OptionsProperty(name = "Name", group = "String", description = "Name", order = 2)
  var name = "testName"
}

class IntBindingProvider: OptionsProvider {
  @OptionsProperty(name = "Name1", group = "Int", description = "Desc", unit = "Unit")
  var intTestOne = 100
}

class StringBindingProvider: OptionsProvider {
  @OptionsProperty(name = "Name1", group = "String", description = "Desc")
  var stringTestOne = "Test"
}

class SliderBindingProvider: OptionsProvider {
  @OptionsProperty(name = "Name1", group = "Slider", description = "Desc", unit = "Unit")
  @Slider(0, 100, 10)
  var sliderTest = 100
}