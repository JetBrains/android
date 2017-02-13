/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.treegrid;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.stream;

/**
 * SpeedSearch for a {@link TreeGrid}
 */
public class TreeGridSpeedSearch<T> extends SpeedSearchBase<TreeGrid<T>> {
  private final Function<T, String> myConverter;
  private boolean myWasPopupRecentlyActive;

  public TreeGridSpeedSearch(@NotNull TreeGrid<T> grid) {
    this(grid, null);
  }

  public TreeGridSpeedSearch(@NotNull TreeGrid<T> grid, @Nullable Function<T, String> converter) {
    super(grid);
    myConverter = converter;
    addChangeListener(event -> popupChange());
  }

  @Override
  protected int getSelectedIndex() {
    int offset = 0;
    List<JList<T>> lists = myComponent.getLists();
    for (JList<T> list : lists) {
      if (list.getSelectedIndex() > -1) {
        return offset + list.getSelectedIndex();
      }
      offset += list.getModel().getSize();
    }
    return -1;
  }

  @NotNull
  @Override
  protected Object[] getAllElements() {
    AbstractTreeStructure model = myComponent.getModel();
    if (model == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    Object root = model.getRootElement();
    Object[] sections = model.getChildElements(root);
    if (sections == null || sections.length == 0) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    return stream(sections).flatMap(section -> stream(model.getChildElements(section))).toArray();
  }

  @Nullable
  @Override
  protected String getElementText(@NotNull Object element) {
    if (myConverter != null) {
      //noinspection unchecked
      return myConverter.apply((T)element);
    }
    return element.toString();
  }

  @Override
  protected void selectElement(@NotNull Object element, String selectedText) {
    //noinspection unchecked
    myComponent.setSelectedElement((T)element);
  }

  // When the SpeedSearch popup goes away we may have moved the selection to a
  // different JList than the JList with the focus. This code moves the focus to the
  // selected JList if the focus is already somewhere in the TreeGrid.
  private void popupChange() {
    boolean isPopupCurrentlyActive = isPopupActive();
    if (!isPopupCurrentlyActive && myWasPopupRecentlyActive && focusInTreeGrid()) {
      myComponent.requestFocus();
    }
    myWasPopupRecentlyActive = isPopupCurrentlyActive;
  }

  private boolean focusInTreeGrid() {
    Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return focus != null && SwingUtilities.isDescendingFrom(focus, myComponent);
  }
}
