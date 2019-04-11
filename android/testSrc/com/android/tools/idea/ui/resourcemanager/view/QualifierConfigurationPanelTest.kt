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
package com.android.tools.idea.ui.resourcemanager.view

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.idea.ui.resourcemanager.viewmodel.QualifierConfigurationViewModel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.UIUtil.findComponentsOfType
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JComboBox
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QualifierConfigurationPanelTest {

  @Test
  fun addQualifierButton() {
    val configurationPanel = QualifierConfigurationPanel(QualifierConfigurationViewModel(FolderConfiguration()))

    assertFalse("Initially, we have en empty row, so there is no need for the add button to be enabled") {
      findAddButton(configurationPanel).isEnabled
    }

    // We now select a value in the first combo box. Because it's not empty anymore, we can add another row
    selectComboBoxFirstItem(configurationPanel, 0)
    val addButton = findAddButton(configurationPanel)
    assertTrue(
      "We have selected a value in the first combo box." +
      " Because it's not empty anymore, we can add another row"
    ) { addButton.isEnabled }


    addButton.doClick()
    assertFalse("We've just added another row, the button should be disabled") { addButton.isEnabled }

    selectComboBoxFirstItem(configurationPanel, 2)
    assertTrue("Now that the second row has a qualifier the button is enabled again") {
      findAddButton(configurationPanel).isEnabled
    }

    clickLastDeleteButton(configurationPanel)
    assertTrue("We have deleted a filled row, the button should still be enabled") {
      addButton.isEnabled
    }

    addButton.doClick()
    assertFalse("A row has been deleted, and we add a new one, so the button should be disabled") {
      addButton.isEnabled
    }

    clickLastDeleteButton(configurationPanel)
    assertTrue("The newly added empty row has been deleted, the button needs to be enabled again") {
      addButton.isEnabled
    }
  }

  @Test
  fun selectedQualifierIsPresentInPopup() {
    val viewModel = QualifierConfigurationViewModel(FolderConfiguration())
    val configurationPanel = QualifierConfigurationPanel(viewModel)

    val comboBox = selectComboBoxFirstItem(configurationPanel, 0)!!
    val selectedItem = comboBox.selectedItem
    comboBox.firePopupMenuWillBecomeVisible()
    assertEquals(selectedItem, comboBox.model.getElementAt(0))
    for (i in 1 until comboBox.model.size) {
      assertNotEquals(selectedItem, comboBox.model.getElementAt(i))
    }
    assertFalse(viewModel.getAvailableQualifiers().contains(selectedItem))
  }

  private fun selectComboBoxFirstItem(configurationPanel: QualifierConfigurationPanel,
                                      comboBoxIndexInPanel: Int): JComboBox<*>? {
    val qualifierCombo = findComponentsOfType(configurationPanel, JComboBox::class.java)[comboBoxIndexInPanel]
    qualifierCombo.firePopupMenuWillBecomeVisible()
    qualifierCombo.selectedIndex = 0
    return qualifierCombo
  }

  private fun clickLastDeleteButton(configurationPanel: QualifierConfigurationPanel) {
    val deleteButton = findComponentsOfType(configurationPanel, ActionButton::class.java).last()
    deleteButton.action.actionPerformed(Mockito.mock(AnActionEvent::class.java))
  }

  private fun findAddButton(configurationPanel: QualifierConfigurationPanel): LinkLabel<*> {
    return findComponentsOfType(configurationPanel, LinkLabel::class.java).first { it.text == "Add another qualifier" }
  }
}
