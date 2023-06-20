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

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.deployer.model.component.Complication.ComplicationType
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.android.tools.adtui.swing.FakeUi;
import com.intellij.openapi.ui.ComboBox
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

@RunWith(JUnit4::class)
class SlotsPanelTest{
  private lateinit var slotsPanel : SlotsPanel
  private lateinit var topSlot: JPanel

  private fun getPanelForSlot(slotNum: Int) =
    ((slotsPanel.slotsUiPanel.getComponent(1) as JComponent).getComponent(0) as JComponent).getComponent(slotNum) as JPanel
  private fun JPanel.getComboBox() = getComponent(2) as ComboBox<*>
  private fun JPanel.getCheckBox() = getComponent(0) as JCheckBox

  @Before
  fun setUp() {
    slotsPanel = SlotsPanel()
    val complicationSlots = listOf(
      ComplicationSlot(
        "Top",
        3,
        arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT, ComplicationType.ICON)
      )
    )
    val model = SlotsPanel.ComplicationsModel(arrayListOf(), complicationSlots, listOf(ComplicationType.LONG_TEXT))
    slotsPanel.setModel(model)
    topSlot = getPanelForSlot(0)
  }

  @Test
  fun allSlotsDisabledByDefault() {
    assertFalse(topSlot.getCheckBox().isSelected)
    assertFalse(topSlot.getComboBox().isEnabled)
  }

  @Test
  fun selectingSlotEnablesComboBox() {
    topSlot.getCheckBox().isSelected = true
    topSlot.getCheckBox().actionListeners.get(0).actionPerformed(ActionEvent(this, 0, ""))
    assertTrue((topSlot.getComponent(2) as ComboBox<*>).isEnabled)
  }

  @Test
  fun noTypeProvidedEverythingDisabled() {
    val complicationSlots = listOf(
      ComplicationSlot(
        "Top",
        3,
        arrayOf(ComplicationType.SHORT_TEXT)
      )
    )
    val model = SlotsPanel.ComplicationsModel(arrayListOf(), complicationSlots, listOf(ComplicationType.LONG_TEXT))
    slotsPanel.setModel(model)
    topSlot = getPanelForSlot(0)

    assertFalse(topSlot.getCheckBox().isEnabled)
    assertFalse(topSlot.getCheckBox().isSelected)
    assertFalse(topSlot.getComboBox().isEnabled)
  }

  @Test
  fun switchAllTypes() {
    val allTypes = arrayOf(ComplicationType.SHORT_TEXT,
                           ComplicationType.LONG_TEXT,
                           ComplicationType.ICON,
                           ComplicationType.RANGED_VALUE,
                           ComplicationType.LARGE_IMAGE,
                           ComplicationType.SMALL_IMAGE,
                           ComplicationType.LARGE_IMAGE)
    val typesToSet = arrayOf(ComplicationType.SHORT_TEXT,
                           ComplicationType.ICON,
                           ComplicationType.LONG_TEXT,
                           ComplicationType.RANGED_VALUE,
                           ComplicationType.LARGE_IMAGE)
    val complicationSlots = listOf(
      ComplicationSlot("Top", 0, allTypes),
      ComplicationSlot("Right", 1, allTypes),
      ComplicationSlot("Bottom", 2, allTypes),
      ComplicationSlot("Left", 3, allTypes),
      ComplicationSlot("Background", 4, allTypes)
    )
    val model = SlotsPanel.ComplicationsModel(arrayListOf(), complicationSlots, allTypes.toList())
    slotsPanel.setModel(model)

    for (i in complicationSlots.indices) {
      getPanelForSlot(i).getCheckBox().isSelected = true
      getPanelForSlot(i).getCheckBox().actionListeners[0].actionPerformed(ActionEvent(this, 0, ""))
      getPanelForSlot(i).getComboBox().selectedItem = typesToSet[i]
    }
    slotsPanel.size = Dimension(1000, 1000)
    val myUi = FakeUi(slotsPanel, 1.0)

    ImageDiffUtil.assertImageSimilar(TestUtils.resolveWorkspacePathUnchecked(GOLDEN_IMAGE_PATH), myUi.render(), 1.0)
  }
}

private const val GOLDEN_IMAGE_PATH = "tools/adt/idea/android/testData/run/golden/goldenComplicationsPanel.png"