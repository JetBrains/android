/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.editors

import com.android.tools.adtui.TreeWalker
import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.ActionLink
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.JButton
import javax.swing.JPanel

@RunWith(JUnit4::class)
class SlotsPanelTest{
  private lateinit var slotsPanel : SlotsPanel
  private lateinit var addButton : ActionLink
  private lateinit var model : SlotsPanel.ComplicationsModel

  private fun JPanel.getIdComboBoxForSlot(slotNum: Int) = (getComponent(slotNum) as JPanel).getComponent(1) as ComboBox<*>
  private fun JPanel.getTypeComboBoxForSlot(slotNum: Int) = (getComponent(slotNum) as JPanel).getComponent(3) as ComboBox<*>
  private fun JPanel.getDeleteButtonForSlot(slotNum: Int) = (getComponent(slotNum) as JPanel).getComponent(4) as JButton

  @Before
  fun setUp() {
    slotsPanel = SlotsPanel()
    addButton = TreeWalker(slotsPanel).descendants().filterIsInstance<ActionLink>().first()
    val complicationSlots = listOf(
      ComplicationSlot(
        "Top",
        3,
        arrayOf(Complication.ComplicationType.SHORT_TEXT, Complication.ComplicationType.LONG_TEXT)
      )
    )
    model = SlotsPanel.ComplicationsModel(arrayListOf(), complicationSlots, listOf(Complication.ComplicationType.LONG_TEXT))

  }

  @Test
  fun addSlotDisabledByDefault() {
    assertThat(addButton.isEnabled).isFalse()
  }

  @Test
  fun updateSlotsEnablesAddSlot() {
    slotsPanel.setModel(model)

    assertThat(addButton.isEnabled).isTrue()
  }

  @Test
  fun noComponentDisablesAddSlot() {
    slotsPanel.setModel(SlotsPanel.ComplicationsModel())

    assertThat(addButton.isEnabled).isFalse()
  }

  @Test
  fun allSlotsAddedDisablesAddSlot() {
    slotsPanel.setModel(model)
    addButton.doClick()

    assertThat(addButton.isEnabled).isFalse()
  }

  @Test
  fun addSlots() {
    slotsPanel.setModel(model)
    addButton.doClick()
    val slotIdComboBox = slotsPanel.slotsComponent.getIdComboBoxForSlot(0)
    val slotTypeComboBox = slotsPanel.slotsComponent.getTypeComboBoxForSlot(0)
    assertThat(slotIdComboBox.isEnabled).isTrue()
    assertThat(slotTypeComboBox.isEnabled).isTrue()
    slotIdComboBox.item = 3
    slotTypeComboBox.item = Complication.ComplicationType.LONG_TEXT

    assertThat(model.currentChosenSlots).hasSize(1)
    assertThat(model.currentChosenSlots.find { it.id == 3 }!!.type).isEqualTo(Complication.ComplicationType.LONG_TEXT)
  }

  @Test
  fun addAndRemoveSlot() {
    slotsPanel.setModel(model)
    addButton.doClick()
    slotsPanel.slotsComponent.getDeleteButtonForSlot(0).doClick()

    assertThat(model.currentChosenSlots).isEmpty()
  }
}