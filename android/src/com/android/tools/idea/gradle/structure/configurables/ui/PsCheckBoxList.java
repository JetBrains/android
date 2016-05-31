/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CheckBoxListListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsCheckBoxList<T> extends CheckBoxList<T> {
  @NotNull private final List<CheckBoxListListener> myListeners = Lists.newCopyOnWriteArrayList();

  @Nullable private SelectionChangeListener<ImmutableList<T>> mySelectionChangeListener;

  public PsCheckBoxList(@NotNull List<T> items) {
    super();
    setItems(items, null);
    super.setCheckBoxListListener((index, value) -> {
      for (CheckBoxListListener listener : myListeners) {
        listener.checkBoxSelectionChanged(index, value);
      }
    });
    addCheckBoxListListener((index, value) -> fireSelectionChangedEvent());
  }

  /**
   * @deprecated use {@link #addCheckBoxListListener(CheckBoxListListener)} instead.
   */
  @Deprecated
  @Override
  public void setCheckBoxListListener(CheckBoxListListener checkBoxListListener) {
    throw new UnsupportedOperationException("Invoke 'setSelectionChangeListener' instead");
  }

  public void addCheckBoxListListener(@NotNull CheckBoxListListener checkBoxListListener) {
    myListeners.add(checkBoxListListener);
  }

  @NotNull
  public AnAction createSelectAllAction() {
    return new AnAction("Select All", "", AllIcons.Actions.Selectall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        setItemsSelected(true);
      }
    };
  }

  @NotNull
  public AnAction createUnselectAllAction() {
    return new AnAction("Unselect All", "", AllIcons.Actions.Unselectall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        setItemsSelected(false);
      }
    };
  }

  public void setItemsSelected(boolean selected) {
    int itemsCount = getItemsCount();
    for (int i = 0; i < itemsCount; i++) {
      T item = getItemAt(i);
      setItemSelected(item, selected);
    }
    repaint();
    fireSelectionChangedEvent();
  }

  private void fireSelectionChangedEvent() {
    if (mySelectionChangeListener != null) {
      mySelectionChangeListener.selectionChanged(getSelectedItems());
    }
  }

  @NotNull
  public ImmutableList<T> getSelectedItems() {
    int count = getItemsCount();
    if (count == 0) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<T> listBuilder = ImmutableList.builder();
    for (int i = 0; i < count; i++) {
      T item = getItemAt(i);
      if (item != null && isItemSelected(item)) {
        listBuilder.add(item);
      }
    }
    return listBuilder.build();
  }

  public void setSelectionChangeListener(@Nullable SelectionChangeListener<ImmutableList<T>> listener) {
    mySelectionChangeListener = listener;
  }
}
