/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.editor

import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ex.ActionUtil.createEmptyEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.ToolbarDecorator.findAddButton
import com.intellij.ui.ToolbarDecorator.findRemoveButton
import com.intellij.util.ui.UIUtil.findComponentOfType
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [AndroidTestExtraParamsTable].
 */
class AndroidTestExtraParamsTableTest {

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  @Test
  @RunsInEdt
  fun testAddAndDeleteButton() {
    // Creates the table with add/delete element button.
    val table = AndroidTestExtraParamsTable(true, false)
    assertThat(table.tableView.tableViewModel.items).isEmpty()

    // Make sure add button is displayed and tapping the button creates an empty element.
    val addButton = requireNotNull(findAddButton(table.component))
    assertThat(addButton.isVisible).isTrue()
    assertThat(addButton.isEnabled).isTrue()
    addButton.actionPerformed(createEmptyEvent())
    dispatchAllEventsInIdeEventQueue();
    assertThat(table.tableView.tableViewModel.items).containsExactly(AndroidTestExtraParam())

    // Make sure delete button is displayed and tapping the button deletes the element.
    table.tableView.addSelection(AndroidTestExtraParam())
    val deleteButton = requireNotNull(findRemoveButton(table.component))
    assertThat(deleteButton.isVisible).isTrue()
    assertThat(deleteButton.isEnabled).isTrue()
    deleteButton.actionPerformed(createEmptyEvent())
    dispatchAllEventsInIdeEventQueue();
    assertThat(table.tableView.tableViewModel.items).isEmpty()
  }

  @Test
  fun testAddAndDeleteButtonIsNotDisplayed() {
    // Creates the table without add/delete element button.
    val table = AndroidTestExtraParamsTable(false, false)

    // Make sure add and delete buttons are not displayed.
    assertThat(findAddButton(table.component)).isNull()
    assertThat(findRemoveButton(table.component)).isNull()
  }

  @Test
  @RunsInEdt
  fun testRevertButton() {
    // Creates the table with revert element button.
    val table = AndroidTestExtraParamsTable(false, true)
    val params = listOf(
      AndroidTestExtraParam("name1", "value1", "original value1", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("name2", "value2", "original value2", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("name3", "value3", "original value3", AndroidTestExtraParamSource.GRADLE)
    )
    table.setValues(params)

    // Select only first two items to be reverted.
    table.tableView.addSelection(params[0])
    table.tableView.addSelection(params[1])

    // Make sure revert button is displayed and tapping the button reverts the modification on the element.
    val availableActions = requireNotNull(findComponentOfType(table.component, CommonActionsPanel::class.java)).toolbar.run {
      PlatformTestUtil.waitForFuture(updateActionsAsync())
      actions
    }
    val revertAction = availableActions.first { action ->
      action.templatePresentation.icon == AllIcons.Actions.Rollback
    }
    revertAction.actionPerformed(createEmptyEvent())
    dispatchAllEventsInIdeEventQueue()

    // Make sure only first two items are reverted.
    assertThat(table.tableView.tableViewModel.items).containsExactly(
      AndroidTestExtraParam("name1", "original value1", "original value1", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("name2", "original value2", "original value2", AndroidTestExtraParamSource.GRADLE),
      AndroidTestExtraParam("name3", "value3", "original value3", AndroidTestExtraParamSource.GRADLE)
    )
  }

  @Test
  fun testRevertButtonIsNotDisplayed() {
    // Creates the table without add/delete element button.
    val table = AndroidTestExtraParamsTable(false, false)

    // Make sure add and delete buttons are not displayed.
    val availableActions = requireNotNull(findComponentOfType(table.component, CommonActionsPanel::class.java)).toolbar.actions
    assertThat(availableActions.any { action -> action.templatePresentation.icon == AllIcons.Actions.Rollback }).isFalse()
  }
}