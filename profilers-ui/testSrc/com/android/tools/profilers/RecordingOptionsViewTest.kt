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
package com.android.tools.profilers

import com.android.tools.adtui.TreeWalker
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.swing.MutableComboBoxModel

@RunWith(Parameterized::class)
class RecordingOptionsViewTest(configs: Array<RecordingOption>, editAction: ((MutableComboBoxModel<RecordingOption>) -> Unit)?) {
  val model = RecordingOptionsModel(RecordingOptionsModelTest.BuiltIns, configs)
  val view = RecordingOptionsView(model, editConfig = editAction)

  @Test
  fun `start button only enabled after a selection`() {
    view.allRadios.forEach { assertThat(it.isSelected).isFalse() }
    assertThat(view.startStopButton.isEnabled).isFalse()
    view.allRadios[0].doClick()
    assertThat(view.startStopButton.isEnabled).isTrue()
  }

  @Test
  fun `config menu disabled when config list is empty`() {
    assumeTrue(model.customConfigurationModel.size == 0)
    view.configComponents.assumedNotNull {
      assertThat(radio.isEnabled).isFalse()
      model.customConfigurationModel.addElement(RecordingOptionsModelTest.CustomConfigs[0])
      assertThat(radio.isEnabled).isTrue()
    }
  }

  @Test
  fun `config menu enabled when there is custom config`() {
    view.configComponents.assumedNotNull {
      while (model.customConfigurationModel.size > 0) {
        assertThat(radio.isEnabled).isTrue()
        model.customConfigurationModel.removeElementAt(0)
      }
      assertThat(radio.isEnabled).isFalse()
    }
  }

  @Test
  fun `selecting radio button updates selected option`() {
    view.allRadios[0].doClick()
    assertThat(model.selectedOption).isEqualTo(model.builtInOptions[0])
  }

  @Test
  fun `selecting custom configuration updates selected option`() {
    assumeTrue(model.customConfigurationModel.size > 0)
    view.configComponents.assumedNotNull {
      menu.selectedItem = menu.getItemAt(0)
      assertThat(model.selectedOption).isNull()
      radio.doClick()
      assertThat(model.selectedOption).isEqualTo(model.customConfigurationModel.getElementAt(0))
    }
  }

  @Test
  fun `radio button reflects model change`() {
    model.selectBuiltInOption(model.builtInOptions[0])
    assertThat(view.allRadios[0].isSelected).isTrue()
  }

  @Test
  fun `config menu reflects model change`() {
    assumeTrue(model.customConfigurationModel.size > 0)
    view.configComponents.assumedNotNull {
      val toSelect = model.customConfigurationModel.getElementAt(0)
      model.customConfigurationModel.selectedItem = toSelect
      model.selectCurrentCustomConfiguration()
      assertThat(radio.isSelected).isTrue()
      assertThat(menu.selectedItem).isEqualTo(toSelect)
    }
  }

  @Test
  fun `start button disabled after pressed`() = view.builtInRadios.forEach { radio ->
    radio.doClick()
    with (view.startStopButton) {
      assertThat(text).isEqualTo(RecordingOptionsView.START)
      assertThat(isEnabled).isTrue()
      doClick()

      if (this@RecordingOptionsViewTest.model.canStop()) {
        assertThat(text).isEqualTo(RecordingOptionsView.STOP)
        assertThat(isEnabled).isTrue()
      } else {
        assertThat(text).isEqualTo(RecordingOptionsView.RECORDING)
        assertThat(isEnabled).isFalse()
      }
    }
    model.setFinished()
  }


  @Test
  fun `all options disabled when recording`() {
    assumeTrue(view.builtInRadios.isNotEmpty())
    assumeTrue(model.builtInOptions[0].stopAction == null)
    view.builtInRadios[0].doClick()
    view.startStopButton.doClick()
    view.allRadios.forEach { assertThat(it.isEnabled).isFalse() }
    view.configComponents?.menu?.let { assertThat(it.isEnabled).isFalse() }
  }

  @Test
  fun `clicking on edit buttons perform the actions`() {
    assumeTrue(model.customConfigurationModel.size == 0)
    view.configComponents.assumedNotNull {
      button.doClick()
      assertThat(model.customConfigurationModel.size).isEqualTo(1)
      button.doClick()
      assertThat(model.customConfigurationModel.size).isEqualTo(0)
    }
  }

  @Test
  fun `view adapts to size`() {
    val grid = TreeWalker(view).descendantStream().filter { it is FlexibleGrid }.findAny().get() as FlexibleGrid
    // Wide by default
    assertThat(grid.mode).isEqualTo(FlexibleGrid.Mode.Wide)
    // Tall if not enough width for 2 columns but tall enough
    grid.adapt((grid.doubleColumnWidth + grid.singleColumnWidth) / 2, grid.singleColumnHeight + 1)
    assertThat(grid.mode).isEqualTo(FlexibleGrid.Mode.Tall)
    // Compact if nothing else works
    grid.adapt(1, 1)
    assertThat(grid.mode).isEqualTo(FlexibleGrid.Mode.Compact)
  }

  @Test
  fun `disabling and re-enabling restores the same states in components`() {
    view.apply {
      checkEnabledSameBeforeAfter()
      allRadios[0].doClick()
      checkEnabledSameBeforeAfter()
      startStopButton.doClick()
      checkEnabledSameBeforeAfter()
    }
    model.apply{
      setOptionNotReady(model.builtInOptions[0], "not ready yet")
      checkEnabledSameBeforeAfter()
      setOptionReady(model.builtInOptions[0])
      checkEnabledSameBeforeAfter()
    }
  }

  @Test
  fun `unready option disabled`() {
    model.setOptionNotReady(model.builtInOptions[0], "not ready")
    assertThat(view.allRadios[0].isEnabled).isFalse()
    assertThat(view.allRadios[0].toolTipText).isNotEmpty()
    model.setOptionReady(model.builtInOptions[0])
    assertThat(view.allRadios[0].isEnabled).isTrue()
    assertThat(view.allRadios[0].toolTipText).isNull()
  }

  private fun checkEnabledSameBeforeAfter() {
    view.apply {
      // Remember state before disabling/enabling
      val startStopButtonEnabled = startStopButton.isEnabled
      val builtInRadiosEnabled = builtInRadios.map { it.isEnabled }
      val configComponentsEnabled = configComponents?.let { Triple(it.radio.isEnabled, it.menu.isEnabled, it.button.isEnabled) }

      // Check everything disabled
      isEnabled = false
      assertThat(startStopButton.isEnabled).isFalse()
      builtInRadios.forEach { assertThat(it.isEnabled).isFalse() }
      configComponents?.apply {
        assertThat(radio.isEnabled).isFalse()
        assertThat(menu.isEnabled).isFalse()
        assertThat(button.isEnabled).isFalse()
      }

      // Check everything restored
      isEnabled = true
      assertThat(startStopButton.isEnabled).isEqualTo(startStopButtonEnabled)
      (builtInRadiosEnabled zip builtInRadios).forEach { (before, radio) -> assertThat(radio.isEnabled).isEqualTo(before) }
      configComponents?.apply {
        val (radioEnabled, menuEnabled, buttonEnabled) = configComponentsEnabled!!
        assertThat(radio.isEnabled).isEqualTo(radioEnabled)
        assertThat(menu.isEnabled).isEqualTo(menuEnabled)
        assertThat(button.isEnabled).isEqualTo(buttonEnabled)
      }
    }
  }

  companion object {
    private val EditConfigActions = arrayOf<((MutableComboBoxModel<RecordingOption>) -> Unit)?>(
      null,
      { if (it.size > 0) it.removeElementAt(0)
        else it.addElement(RecordingOptionsModelTest.CustomConfigs[0]) }
    )

    @Parameterized.Parameters @JvmStatic
    fun configs() = RecordingOptionsModelTest.configs().flatMap { config ->
      EditConfigActions.map { action ->
        (config.toList() + action).toTypedArray()
      }
    }.toTypedArray()
  }
}

private fun<T> T?.assumedNotNull(run: T.() -> Any) {
  assumeNotNull(this)
  this!!.run()
}