/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.deployer.model.component.Complication.ComplicationType
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.android.tools.idea.run.configuration.ComplicationWatchFaceInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.ActionLink
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import javax.swing.JPanel


class AndroidComplicationConfigurationEditorTes : AndroidTestCase() {

  private lateinit var runConfiguration: AndroidComplicationConfiguration
  private lateinit var settingsEditor: AndroidComplicationConfigurationEditor

  private val <T> ComboBox<T>.items get() = (0 until itemCount).map { getItemAt(it) }

  private fun JPanel.getIdComboBoxForSlot(slotNum: Int) = (getComponent(slotNum) as JPanel).getComponent(1) as ComboBox<*>
  private fun JPanel.getTypeComboBoxForSlot(slotNum: Int) = (getComponent(slotNum) as JPanel).getComponent(3) as ComboBox<*>

  override fun setUp() {
    super.setUp()
    val runConfigurationFactory = AndroidComplicationConfigurationType().configurationFactories[0]
    runConfiguration = AndroidComplicationConfiguration(project, runConfigurationFactory)
    settingsEditor = runConfiguration.configurationEditor
  }

  fun testResetFromAndApplyTo() {
    runConfiguration.componentName = "com.example.Complication"
    runConfiguration.setModule(myModule)
    runConfiguration.watchFaceInfo = object : ComplicationWatchFaceInfo {
      override val complicationSlots = listOf(
        ComplicationSlot(
          "Top",
          0,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE)
        ),
        ComplicationSlot(
          "Right",
          2,
          arrayOf(ComplicationType.SHORT_TEXT, ComplicationType.MONOCHROMATIC_IMAGE)
        ))
      override val apk = ""
      override val appId = ""
      override val watchFaceFQName = ""
    }

    val editor = settingsEditor.component as DialogPanel
    val slotsPanel = editor.components.firstIsInstance<JPanel>()
    val addButton = TreeWalker(editor).descendants().filterIsInstance<ActionLink>().first()
    assertThat(addButton.isEnabled).isFalse()

    settingsEditor.resetFrom(runConfiguration)
    val modulesComboBox = TreeWalker(editor).descendants().filterIsInstance<ModulesComboBox>().first()
    assertEquals(myModule, modulesComboBox.selectedModule)

    // runConfiguration has available slots, add button should become enabled.
    assertThat(addButton.isEnabled).isTrue()
    // runConfiguration doesn't have chosen components.
    assertThat(slotsPanel.components).isEmpty()

    // Add slot.
    addButton.doClick()
    assertThat(addButton.isEnabled).isTrue()
    assertThat(slotsPanel.components).hasLength(1)

    var slotIdComboBox1 = slotsPanel.getIdComboBoxForSlot(0)
    assertThat(slotIdComboBox1.items).containsExactly(0, 2)
    assertEquals(0, slotIdComboBox1.item)

    val slotTypeComboBox1 = slotsPanel.getTypeComboBoxForSlot(0)
    assertThat(slotTypeComboBox1.items).containsExactly(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE)

    // Add slot.
    addButton.doClick()
    // runConfiguration has only 2 available slots.
    assertThat(addButton.isEnabled).isFalse()
    assertThat(slotsPanel.components).hasLength(2)

    slotIdComboBox1 = slotsPanel.getIdComboBoxForSlot(0)
    // After we added second slot, only one option is available in the first slot.
    assertThat(slotIdComboBox1.items).containsExactly(0)

    val slotIdComboBox2 = slotsPanel.getIdComboBoxForSlot(1)
    assertThat(slotIdComboBox2.items).containsExactly(2)
    assertEquals(2, slotIdComboBox2.item)

    val slotTypeComboBox2 = slotsPanel.getTypeComboBoxForSlot(1)
    assertThat(slotTypeComboBox2.items).containsExactly(ComplicationType.SHORT_TEXT, ComplicationType.MONOCHROMATIC_IMAGE)

    assertThat(editor.isModified()).isTrue()

    // Saving configuration.
    settingsEditor.applyTo(runConfiguration)

    assertThat(runConfiguration.chosenSlots).hasSize(2)
    assertThat(runConfiguration.chosenSlots.find { it.id == 0 }!!.type).isEqualTo(ComplicationType.SHORT_TEXT)

    //Changing type.
    slotsPanel.getTypeComboBoxForSlot(0).item = ComplicationType.RANGED_VALUE

    assertThat(editor.isModified()).isTrue()

    // Saving configuration.
    settingsEditor.applyTo(runConfiguration)

    assertThat(editor.isModified()).isFalse()

    assertThat(runConfiguration.chosenSlots).hasSize(2)
    assertThat(runConfiguration.chosenSlots.find { it.id == 0 }!!.type).isEqualTo(ComplicationType.RANGED_VALUE)

    //Changing type.
    slotsPanel.getTypeComboBoxForSlot(0).item = ComplicationType.SHORT_TEXT
    assertThat(editor.isModified()).isTrue()

    //Check that without applying we don't update configuration
    assertThat(runConfiguration.chosenSlots.find { it.id == 0 }!!.type).isEqualTo(ComplicationType.RANGED_VALUE)
  }

}