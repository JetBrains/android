/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.preview.inspector

import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.common.inspector.PsiPropertyDropDown
import com.android.tools.idea.compose.pickers.preview.enumsupport.PreviewPickerValuesProvider
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.config.PARAMETER_API_LEVEL
import com.android.tools.preview.config.PARAMETER_FONT_SCALE
import com.android.tools.preview.config.PARAMETER_GROUP
import com.android.tools.preview.config.PARAMETER_HARDWARE_CHIN_SIZE
import com.android.tools.preview.config.PARAMETER_HARDWARE_CUTOUT
import com.android.tools.preview.config.PARAMETER_HARDWARE_DENSITY
import com.android.tools.preview.config.PARAMETER_HARDWARE_DEVICE
import com.android.tools.preview.config.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.preview.config.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.preview.config.PARAMETER_HARDWARE_IS_ROUND
import com.android.tools.preview.config.PARAMETER_HARDWARE_NAVIGATION
import com.android.tools.preview.config.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.preview.config.PARAMETER_HARDWARE_WIDTH
import com.android.tools.preview.config.PARAMETER_LOCALE
import com.android.tools.preview.config.PARAMETER_NAME
import com.android.tools.preview.config.PARAMETER_SHOW_BACKGROUND
import com.android.tools.preview.config.PARAMETER_SHOW_SYSTEM_UI
import com.android.tools.preview.config.PARAMETER_UI_MODE
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.android.tools.property.panel.impl.ui.PropertyCheckBox
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.google.common.collect.HashBasedTable
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TestPsiPropertyItem(override var name: String, override var value: String?) : PsiPropertyItem

class PreviewPropertiesInspectorBuilderTest {
  @get:Rule val rule = AndroidProjectRule.inMemory()

  private val module
    get() = rule.fixture.module

  private lateinit var inspectorPanel: FakeInspectorPanel
  private lateinit var previewPropertiesInspectorBuilder: PreviewPropertiesInspectorBuilder

  @Before
  fun setup() {
    val enumSupportValuesProvider =
      PreviewPickerValuesProvider.createPreviewValuesProvider(module, null)
    inspectorPanel = FakeInspectorPanel()
    previewPropertiesInspectorBuilder = PreviewPropertiesInspectorBuilder(enumSupportValuesProvider)
  }

  @Test
  fun `test attach empty table to inspector`() {
    val propertiesTable = PropertiesTable.emptyTable<PsiPropertyItem>()

    // Adding an empty table expects to raise a NullPointerException because all the 3 dimensions
    // parameters of the hardware panel are needed.
    try {
      previewPropertiesInspectorBuilder.attachToInspector(inspectorPanel, propertiesTable)
      fail("Expected to throw a NullPointerException")
    } catch (_: NullPointerException) {
      // Do nothing. Expected to go to the catch block.
    }
  }

  @Test
  fun `test attach missing Dimension information in hardware view raises null pointer exception`() {
    val table = HashBasedTable.create<String, String, PsiPropertyItem>()
    val propertiesTable =
      PropertiesTable.create(table).apply {
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_WIDTH, "45"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_HEIGHT, "42"))
      }

    // Dimensions should have all the items: width, height and dim unit. One of this missing
    // information will cause a NullPointerException.
    try {
      previewPropertiesInspectorBuilder.attachToInspector(inspectorPanel, propertiesTable)
      fail("Expected to throw a NullPointerException")
    } catch (_: NullPointerException) {
      // Do nothing. Expected to go to the catch block.
    }
  }

  @Test
  fun `test all Dimension information in hardware doesn't raise null pointer exception`() {
    val table = HashBasedTable.create<String, String, PsiPropertyItem>()
    val propertiesTable =
      PropertiesTable.create(table).apply {
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_WIDTH, "45"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_HEIGHT, "42"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_DIM_UNIT, "px"))
      }

    // Dimensions should have all the items: width, height and dim unit. One of this missing
    // information will cause a NullPointerException.
    try {
      previewPropertiesInspectorBuilder.attachToInspector(inspectorPanel, propertiesTable)
    } catch (_: NullPointerException) {
      fail("Should not throw a NullPointerException")
    }
  }

  @Test
  fun `test add properties table in inspector builder`() {
    val table = HashBasedTable.create<String, String, PsiPropertyItem>()

    // Properties table containing all the information to be shown in the three regions of the
    // PreviewPicker
    val propertiesTable =
      PropertiesTable.create(table).apply {
        // Header region
        put(TestPsiPropertyItem(PARAMETER_NAME, "test name"))
        put(TestPsiPropertyItem(PARAMETER_GROUP, "test group"))
        // Hardware region
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_DEVICE, "Pixel Banana"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_WIDTH, "120"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_HEIGHT, "42"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_DIM_UNIT, "px"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_DENSITY, "234"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_ORIENTATION, "landscape"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_IS_ROUND, "true"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_CHIN_SIZE, "14dp"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_CUTOUT, "none"))
        put(TestPsiPropertyItem(PARAMETER_HARDWARE_NAVIGATION, "gesture"))
        // Display Region
        put(TestPsiPropertyItem(PARAMETER_API_LEVEL, "16"))
        put(TestPsiPropertyItem(PARAMETER_LOCALE, "Default (en-US)"))
        put(TestPsiPropertyItem(PARAMETER_FONT_SCALE, "1.6"))
        put(TestPsiPropertyItem(PARAMETER_SHOW_SYSTEM_UI, "false"))
        put(TestPsiPropertyItem(PARAMETER_SHOW_BACKGROUND, "true"))
        put(TestPsiPropertyItem(PARAMETER_UI_MODE, "Car"))
      }

    // Attach to the inspector the properties table.
    previewPropertiesInspectorBuilder.attachToInspector(inspectorPanel, propertiesTable)

    // In the Preview Picker we have added the 3 regions:
    // * Preview Configuration (indexes: 0, 1)
    // * Hardware (index 3: Is a JPanel containing 16 components)
    // * Display (indexes: 4, 5, 6, 7, 8, 9, 10)
    assertEquals(11, inspectorPanel.lines.size)

    // Test name line.
    assertEquals("name", inspectorPanel.lines[0].editorModel?.property?.name)
    assertEquals("test name", inspectorPanel.lines[0].editorModel?.property?.value)

    // Test group line.
    assertEquals("group", inspectorPanel.lines[1].editorModel?.property?.name)
    assertEquals("test group", inspectorPanel.lines[1].editorModel?.property?.value)

    // Test "Hardware" title
    val hardwareLine = inspectorPanel.lines[2].component as JPanel
    val hardwareTitleLabel = hardwareLine.components.first() as JLabel
    assertEquals("Hardware", hardwareTitleLabel.text)

    // Test hardware region.
    // The hardware region is organized within its own JPanel of index 3.
    testHardwarePanelComponents((inspectorPanel.lines[3].component as JPanel).components)

    // Test display region.
    // The display region is not packed within its own Panel, the display section starts from index
    // 4.
    val displayInspectorPanelLines = inspectorPanel.lines

    // Test title.
    val displayLine = displayInspectorPanelLines[4].component as JPanel
    val displayTitleLabel = displayLine.components.first() as JLabel
    assertEquals("Display", displayTitleLabel.text)
    // Test parameters.
    assertEquals("apiLevel", displayInspectorPanelLines[5].editorModel?.property?.name)
    assertEquals("16", displayInspectorPanelLines[5].editorModel?.property?.value)
    assertEquals("locale", displayInspectorPanelLines[6].editorModel?.property?.name)
    assertEquals("Default (en-US)", displayInspectorPanelLines[6].editorModel?.property?.value)
    assertEquals("fontScale", displayInspectorPanelLines[7].editorModel?.property?.name)
    assertEquals("1.6", displayInspectorPanelLines[7].editorModel?.property?.value)
    assertEquals("showSystemUi", displayInspectorPanelLines[8].editorModel?.property?.name)
    assertEquals("false", displayInspectorPanelLines[8].editorModel?.property?.value)
    assertEquals("showBackground", displayInspectorPanelLines[9].editorModel?.property?.name)
    assertEquals("true", displayInspectorPanelLines[9].editorModel?.property?.value)
    assertEquals("uiMode", displayInspectorPanelLines[10].editorModel?.property?.name)
    assertEquals("Car", displayInspectorPanelLines[10].editorModel?.property?.value)
  }

  /** Covers [HardwarePanelHelper] test cases. */
  private fun testHardwarePanelComponents(hardwarePanelComponents: Array<out Component>) {
    // Panel has exactly 16 components
    assertEquals(16, hardwarePanelComponents.size)

    // Check if all the items are correctly added in the Hardware region.
    assertEquals("Device", (hardwarePanelComponents[0] as JLabel).text)
    assertEquals("Pixel Banana", hardwarePanelComponents[1].getDisplayNameFromDropDown())

    // Check dimensions line.
    assertEquals("Dimensions", (hardwarePanelComponents[2] as JLabel).text)

    // Dimensions value are defined in their own JPanel with the format of "[width] x [height] [dim
    // unit dropdown]".
    val dimensionsLabelComponents = (hardwarePanelComponents[3] as JPanel).components
    assertEquals("120", (dimensionsLabelComponents[0] as PropertyTextField).text)
    assertEquals("x", (dimensionsLabelComponents[1] as JLabel).text)
    assertEquals("42", (dimensionsLabelComponents[2] as PropertyTextField).text)
    assertEquals("px", dimensionsLabelComponents[3].getDisplayNameFromDropDown())

    // Check density line.
    assertEquals("Density", (hardwarePanelComponents[4] as JLabel).text)
    assertEquals("hdpi (240 dpi)", hardwarePanelComponents[5].getDisplayNameFromDropDown())

    // Check orientation line.
    assertEquals("Orientation", (hardwarePanelComponents[6] as JLabel).text)
    assertEquals("landscape", hardwarePanelComponents[7].getDisplayNameFromDropDown())

    // Check "is round" line, it consists on a three-state check-box with a text field
    assertEquals("IsRound", (hardwarePanelComponents[8] as JLabel).text)
    val isRoundCheckTextField =
      (hardwarePanelComponents[9] as PropertyCheckBox).components[1] as PropertyTextField
    assertEquals("true", isRoundCheckTextField.text)

    // Check chin size line.
    assertEquals("ChinSize", (hardwarePanelComponents[10] as JLabel).text)
    assertEquals("14dp", (hardwarePanelComponents[11] as PropertyTextField).text)

    // Check cutout line.
    assertEquals("Cutout", (hardwarePanelComponents[12] as JLabel).text)
    assertEquals("none", hardwarePanelComponents[13].getDisplayNameFromDropDown())

    // Check navigation line.
    assertEquals("Navigation", (hardwarePanelComponents[14] as JLabel).text)
    assertEquals("gesture", hardwarePanelComponents[15].getDisplayNameFromDropDown())
  }

  /**
   * Check if the [Component] is of type [PsiPropertyDropDown] and gets the display name of the
   * selected item of the dropdown menu.
   *
   * @return The display name of the selected item of the [PsiPropertyDropDown]
   * @throws ClassCastException if the [Component] is not of type [PsiPropertyDropDown]
   */
  private fun Component.getDisplayNameFromDropDown(): String {
    val selectedDropDown = this as PsiPropertyDropDown
    return selectedDropDown.getSelectedItemForTest().display
  }
}
