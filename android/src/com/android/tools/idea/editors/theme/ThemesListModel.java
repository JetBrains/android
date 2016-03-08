/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.configurations.ConfigurationListener;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.tools.idea.editors.theme.SeparatedList.group;

public class ThemesListModel extends AbstractListModel implements ComboBoxModel {

  public static final String CREATE_NEW_THEME = "Create New Theme";
  public static final String SHOW_ALL_THEMES = "Show all themes";
  public static final String RENAME = "Rename ";

  private final JSeparator mySeparator = new JSeparator(SwingConstants.HORIZONTAL);
  private final ThemeEditorContext myContext;
  private final ImmutableList<String> myDefaultThemeNames;
  private final String myDefaultThemeName;

  private SeparatedList myAllItems;
  private String mySelectedObject;
  private final List<String> myEditOptions = new ArrayList<String>();
  private ImmutableList<String> myAvailableProjectThemes;

  public ThemesListModel(@NotNull ThemeEditorContext context, @NotNull List<String> defaultThemeNames, @Nullable String defaultThemeName) {
    myContext = context;
    myDefaultThemeNames = ImmutableList.copyOf(defaultThemeNames);
    myDefaultThemeName = defaultThemeName;

    updateThemes();

    myContext.addConfigurationListener(new ConfigurationListener() {
      @Override
      public boolean changed(int flags) {
        if ((flags & ConfigurationListener.MASK_FOLDERCONFIG) != 0) {
          updateThemes();
          fireContentsChanged(this, -1, -1);
        }
        return true;
      }
    });
  }

  /**
   * Updates the themes list reloading all the themes from the resolver
   */
  private void updateThemes() {
    // We sort the themes, displaying the local project themes at the top sorted alphabetically. The non local themes are sorted
    // alphabetically right below the project themes.
    ImmutableList<String> editableThemes = ThemeEditorUtils.getModuleThemeQualifiedNamesList(myContext.getCurrentContextModule());

    ImmutableList.Builder<String> availableThemesListBuilder = ImmutableList.builder();
    ImmutableList.Builder<String> disabledThemesListBuilder = ImmutableList.builder();
    ThemeResolver themeResolver = myContext.getThemeResolver();

    for (String themeName : editableThemes) {
      if (themeResolver.getTheme(themeName) != null) {
        availableThemesListBuilder.add(themeName);
      }
      else {
        disabledThemesListBuilder.add(themeName);
      }
    }

    myAvailableProjectThemes = availableThemesListBuilder.build();
    ImmutableList<String> disabledProjectThemes = disabledThemesListBuilder.build();

    String selectedItem = getSelectedItem();
    if (selectedItem == null) {
      if (myDefaultThemeName != null &&
          (editableThemes.contains(myDefaultThemeName) || themeResolver.getTheme(myDefaultThemeName) != null)) {
        selectedItem = myDefaultThemeName;
      }
      else if (!editableThemes.isEmpty()) {
        selectedItem = editableThemes.get(0);
      }
      else if (!myDefaultThemeNames.isEmpty()) {
        selectedItem = myDefaultThemeNames.get(0);
      }
    }

    myEditOptions.clear();
    buildEditOptionsList(selectedItem);

    myAllItems = new SeparatedList(mySeparator, group(myAvailableProjectThemes), group(disabledProjectThemes),
                                   group(myDefaultThemeNames, SHOW_ALL_THEMES), group(myEditOptions));

    // Set the default selection to the first element.
    setSelectedItem(selectedItem);
  }

  private void buildEditOptionsList(@Nullable String selectedItem) {
    myEditOptions.clear();
    myEditOptions.add(CREATE_NEW_THEME);
    if (selectedItem != null && myAvailableProjectThemes.contains(selectedItem)) {
      myEditOptions.add(RENAME + selectedItem);
    }
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
    if (!(anItem instanceof String)) {
      return;
    }

    mySelectedObject = (String)anItem;
    if (!isSpecialOption(mySelectedObject)) {
      buildEditOptionsList(mySelectedObject);
    }

    fireContentsChanged(this, -1, -1);
  }

  @Nullable
  @Override
  public String getSelectedItem() {
    return mySelectedObject;
  }

  public static boolean isSpecialOption(@NotNull String value) {
    return (SHOW_ALL_THEMES.equals(value) || CREATE_NEW_THEME.equals(value) || value.startsWith(RENAME));
  }
}
