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
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    super(grid, null);
    myConverter = converter;
  }

  public static <T> TreeGridSpeedSearch<T> installOn(@NotNull TreeGrid<T> grid, @Nullable Function<T, String> converter) {
    TreeGridSpeedSearch<T> search = new TreeGridSpeedSearch<>(grid, converter);
    search.setupListeners();
    return search;
  }

  @Override
  public void setupListeners() {
    super.setupListeners();
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
  protected ListIterator<Object> getElementIterator(int startingViewIndex) {
    AbstractTreeStructure model = myComponent.getModel();
    List<Object> list = model == null ? Collections.emptyList() : Arrays.stream(model.getChildElements(model.getRootElement()))
      .flatMap(section -> Arrays.stream(model.getChildElements(section)))
      .collect(Collectors.toUnmodifiableList());
    return list.listIterator(startingViewIndex);
  }

  @Override
  protected int getElementCount() {
    AbstractTreeStructure model = myComponent.getModel();
    return model == null ? 0 : (int)Arrays.stream(model.getChildElements(model.getRootElement()))
      .flatMap(section -> Arrays.stream(model.getChildElements(section))).count();
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
