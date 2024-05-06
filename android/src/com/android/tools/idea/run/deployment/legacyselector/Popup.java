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
package com.android.tools.idea.run.deployment.legacyselector;

import com.android.tools.adtui.util.HelpTooltipForList;
import com.android.tools.idea.run.deployment.Heading;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ui.popup.PopupFactoryImpl.ActionGroupPopup;
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JList;
import javax.swing.ListModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Popup extends ActionGroupPopup {

  Popup(@NotNull ActionGroup group, @NotNull DataContext context, @NotNull Runnable runnable) {
    super(null, group, context, false, true, true, false, runnable, 30, null, null, true);
    setMinimumSize(new Dimension(1, 1));

    @SuppressWarnings("unchecked")
    JList<ActionItem> list = getList();

    list.setCellRenderer(new CellRenderer(this));
    list.setName("deviceAndSnapshotComboBoxList");
    new HelpTooltipForList<ActionItem>().installOnList(this, list, (listIndex, tooltip) -> {
      AnAction action = list.getModel().getElementAt(listIndex).getAction();
      if (action instanceof SelectDeviceAction) {
        return TooltipsKt.updateTooltip(((SelectDeviceAction)action).getDevice().launchCompatibility(), tooltip);
      }
      if (action instanceof SnapshotActionGroup) {
        return TooltipsKt.updateTooltip(((SnapshotActionGroup)action).getDevice().launchCompatibility(), tooltip);
      }
      return false;
    });

    ActionMap map = list.getActionMap();

    map.put("selectNextRow", new SelectNextRow(list));
    map.put("selectPreviousRow", new SelectPreviousRow(list));
  }

  @Override
  protected void disposeAllParents(@Nullable InputEvent event) {
    // There is case when a tooltip is scheduled to show, but the popup is already closed (disposeAllParents is called).
    HelpTooltip.dispose(getList());
    super.disposeAllParents(event);
  }

  private static final class SelectNextRow extends SelectRow {
    private SelectNextRow(@NotNull JList<ActionItem> list) {
      super(list);
    }

    /**
     * @return a cyclic index stream starting from the index after the selected one if "Cyclic scrolling in list" is selected in the
     * settings. If the setting is not selected, returns an index stream starting from the index after the selected one to the end.
     */
    @NotNull
    @Override
    IntStream indexStream() {
      if (UISettings.getInstance().getCycleScrolling()) {
        return IntStream.iterate(nextIndex(myList.getLeadSelectionIndex()), this::nextIndex);
      }

      return IntStream.range(myList.getLeadSelectionIndex() + 1, myList.getModel().getSize());
    }

    private int nextIndex(int currentIndex) {
      int nextIndex = currentIndex + 1;
      return nextIndex == myList.getModel().getSize() ? 0 : nextIndex;
    }
  }

  private static final class SelectPreviousRow extends SelectRow {
    private SelectPreviousRow(@NotNull JList<ActionItem> list) {
      super(list);
    }

    /**
     * @return a cyclic index stream starting from the index before the selected one if "Cyclic scrolling in list" is selected in the
     * settings. If the setting is not selected, returns an index stream starting from the index before the selected one to 0.
     */
    @NotNull
    @Override
    IntStream indexStream() {
      int leadSelectionIndex = myList.getLeadSelectionIndex();

      if (UISettings.getInstance().getCycleScrolling()) {
        return IntStream.iterate(previousIndex(leadSelectionIndex), this::previousIndex);
      }

      return IntStream.range(0, leadSelectionIndex)
        .map(index -> leadSelectionIndex - index - 1);
    }

    private int previousIndex(int currentIndex) {
      int previousIndex = currentIndex - 1;
      return previousIndex == -1 ? myList.getModel().getSize() - 1 : previousIndex;
    }
  }

  private static abstract class SelectRow extends AbstractAction {
    @NotNull final JList<ActionItem> myList;

    private SelectRow(@NotNull JList<ActionItem> list) {
      myList = list;
    }

    /**
     * Traverses the index stream, finds the first non heading action, and selects it
     */
    @Override
    public final void actionPerformed(@NotNull ActionEvent event) {
      ListModel<ActionItem> model = myList.getModel();

      OptionalInt optionalIndex = indexStream()
        .filter(index -> !(model.getElementAt(index).getAction() instanceof Heading))
        .findFirst();

      optionalIndex.ifPresent(index -> myList.getSelectionModel().setSelectionInterval(index, index));
    }

    @NotNull
    abstract IntStream indexStream();
  }
}
