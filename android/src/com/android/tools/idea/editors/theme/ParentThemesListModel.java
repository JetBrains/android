/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.editors.theme.SeparatedList.group;

public class ParentThemesListModel extends AbstractListModel implements MutableComboBoxModel {
  private static final int MAX_SIZE = 5;

  public static final String SHOW_ALL_THEMES = "Show all themes";

  private Object mySelectedObject;
  private final SeparatedList myAllItems;
  private final ArrayList<String> myRecentParentThemeList = new ArrayList<String>();

  /**
   * @param defaultThemeList Default theme list in the combo, usually is taken from {@link ThemeEditorUtils#getDefaultThemeNames(ThemeResolver)}
   */
  public ParentThemesListModel(@NotNull List<String> defaultThemeList, @Nullable String defaultParent) {
    // If default parent is not in the theme list, add it
    if (defaultParent != null && !defaultThemeList.contains(defaultParent)) {
      myRecentParentThemeList.add(defaultParent);
    }

    myAllItems = new SeparatedList(new JSeparator(SwingConstants.HORIZONTAL), group(myRecentParentThemeList),
                                   group(ImmutableList.copyOf(defaultThemeList)), group(SHOW_ALL_THEMES));

    // Set default parent if present, first item otherwise
    setSelectedItem(defaultParent == null ? myAllItems.get(0) : defaultParent);
  }

  @Override
  public int getSize() {
    return myAllItems.size();
  }

  @NotNull
  @Override
  public Object getElementAt(int index) {
    return myAllItems.get(index);
  }

  @Override
  public void setSelectedItem(@Nullable Object anItem) {
    if (anItem instanceof JSeparator) {
      return;
    }
    if (!Objects.equal(mySelectedObject, anItem)) {
      mySelectedObject = anItem;
      fireContentsChanged(this, -1, -1);
    }
  }

  @Nullable
  @Override
  public Object getSelectedItem() {
    return mySelectedObject;
  }

  @Override
  public void addElement(Object obj) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeElement(Object obj) {
    //noinspection SuspiciousMethodCalls
    int index = myRecentParentThemeList.indexOf(obj);
    if (index != -1) {
      removeElementAt(index);
    }
  }

  @Override
  public void insertElementAt(Object obj, int index) {
    assert obj instanceof String;
    myRecentParentThemeList.add(index, (String)obj);
    fireIntervalAdded(this, index, index);
    if (myRecentParentThemeList.size() > MAX_SIZE) {
      removeElementAt(MAX_SIZE);
    }
  }

  @Override
  public void removeElementAt(int index) {
    myRecentParentThemeList.remove(index);
    fireIntervalRemoved(this, index, index);
  }
}
