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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.adtui.util.HelpTooltipForList
import com.android.tools.idea.run.deployment.Heading
import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.ui.popup.PopupFactoryImpl.ActionGroupPopup
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.StartupUiUtil
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.util.stream.IntStream
import javax.swing.AbstractAction
import javax.swing.JList

internal class Popup(group: ActionGroup, context: DataContext, runnable: Runnable) :
  ActionGroupPopup(null, group, context, false, true, true, false, runnable, 30, null, null, true) {
  init {
    setMinimumSize(Dimension(1, 1))
    @Suppress("UNCHECKED_CAST") val list = list as JList<ActionItem>
    list.setCellRenderer(CellRenderer(this))
    list.setName("deviceAndSnapshotComboBoxList")
    HelpTooltipForList<ActionItem>().installOnList(this, list) {
      listIndex: Int,
      tooltip: HelpTooltip ->
      when (val action = list.model.getElementAt(listIndex).action) {
        is SelectDeviceAction -> updateTooltip(action.device.launchCompatibility, tooltip)
        is SnapshotActionGroup -> updateTooltip(action.device.launchCompatibility, tooltip)
        else -> false
      }
    }
    list.actionMap.apply {
      put("selectNextRow", SelectNextRow(list))
      put("selectPreviousRow", SelectPreviousRow(list))
    }
  }

  override fun disposeAllParents(event: InputEvent?) {
    // There is case when a tooltip is scheduled to show, but the popup is already closed
    // (disposeAllParents is called).
    HelpTooltip.dispose(list)
    super.disposeAllParents(event)
  }

  private class CellRenderer(popup: ListPopupImpl) : PopupListElementRenderer<ActionItem>(popup) {
    override fun customizeComponent(
      list: JList<out ActionItem>,
      value: ActionItem,
      selected: Boolean,
    ) {
      super.customizeComponent(list, value, selected)
      myTextLabel.setFont(StartupUiUtil.labelFont)
    }
  }

  private class SelectNextRow(list: JList<ActionItem>) : SelectRow(list) {
    /**
     * @return a cyclic index stream starting from the index after the selected one if "Cyclic
     *   scrolling in list" is selected in the settings. If the setting is not selected, returns an
     *   index stream starting from the index after the selected one to the end.
     */
    override fun indexStream(): IntStream {
      return when {
        UISettings.getInstance().cycleScrolling ->
          IntStream.iterate(nextIndex(list.leadSelectionIndex), ::nextIndex)
        else -> IntStream.range(list.leadSelectionIndex + 1, list.model.size)
      }
    }

    private fun nextIndex(currentIndex: Int): Int = (currentIndex + 1).mod(list.model.size)
  }

  private class SelectPreviousRow(list: JList<ActionItem>) : SelectRow(list) {
    /**
     * @return a cyclic index stream starting from the index before the selected one if "Cyclic
     *   scrolling in list" is selected in the settings. If the setting is not selected, returns an
     *   index stream starting from the index before the selected one to 0.
     */
    override fun indexStream(): IntStream {
      val leadSelectionIndex = list.leadSelectionIndex
      return when {
        UISettings.getInstance().cycleScrolling ->
          IntStream.iterate(previousIndex(leadSelectionIndex), ::previousIndex)
        else ->
          IntStream.range(0, leadSelectionIndex).map { index: Int ->
            leadSelectionIndex - index - 1
          }
      }
    }

    private fun previousIndex(currentIndex: Int): Int = (currentIndex - 1).mod(list.model.size)
  }

  private abstract class SelectRow(val list: JList<ActionItem>) : AbstractAction() {
    /** Traverses the index stream, finds the first non heading action, and selects it */
    override fun actionPerformed(event: ActionEvent) {
      indexStream()
        .filter { list.model.getElementAt(it).action !is Heading }
        .findFirst()
        .ifPresent { index -> list.selectionModel.setSelectionInterval(index, index) }
    }

    abstract fun indexStream(): IntStream
  }
}
