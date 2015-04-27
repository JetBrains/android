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


import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

public class ParentThemesListModel extends AbstractListModel implements ComboBoxModel {
  private static final JSeparator SEPARATOR = new JSeparator(SwingConstants.HORIZONTAL);

  public static final String SHOW_ALL_THEMES = "Show all themes";

  private final ImmutableList<ThemeEditorStyle> myDefaultParentThemeList;
  private final ArrayList<ThemeEditorStyle> myRecentParentThemeList = new ArrayList<ThemeEditorStyle>();
  private Object mySelectedObject;
  private int myNumberSeparators;

  public ParentThemesListModel(@NotNull ImmutableList<ThemeEditorStyle> defaultThemeList, @NotNull ThemeEditorStyle parent) {
    myDefaultParentThemeList = defaultThemeList;
    if (!myDefaultParentThemeList.contains(parent)) {
      myRecentParentThemeList.add(parent);
    }
    setSelectedItem(parent);

    if (!myDefaultParentThemeList.isEmpty()) {
      myNumberSeparators++;
    }
    if (!myRecentParentThemeList.isEmpty()) {
      myNumberSeparators++;
    }
  }

  @Override
  public int getSize() {
    return myRecentParentThemeList.size() + myDefaultParentThemeList.size() + myNumberSeparators + 1;
  }

  @NotNull
  @Override
  public Object getElementAt(int index) {
    if (index == getSize() - 1) {
      return SHOW_ALL_THEMES;
    }
    if (index == getSize() - 2) {
      return SEPARATOR;
    }
    int recentParentThemeNumber = myRecentParentThemeList.size();
    if (recentParentThemeNumber > 0) {
      if (index < recentParentThemeNumber) {
        return myRecentParentThemeList.get(index);
      }
      if (index == recentParentThemeNumber) {
        return SEPARATOR;
      }
      return myDefaultParentThemeList.get(index - recentParentThemeNumber - 1);
    }
    return myDefaultParentThemeList.get(index);
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
}
