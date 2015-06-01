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

import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import static com.android.tools.idea.editors.theme.SeparatedList.group;

public class ThemesListModel extends AbstractListModel implements ComboBoxModel {

  private static final JSeparator SEPARATOR = new JSeparator(SwingConstants.HORIZONTAL);

  public static final String CREATE_NEW_THEME = "Create New Theme";
  public static final String SHOW_ALL_THEMES = "Show all themes";
  public static final String RENAME = "Rename ";

  private SeparatedList myAllItems;
  private Object mySelectedObject;

  private final ArrayList<String> myEditThemeOptions = new ArrayList<String>();

  public ThemesListModel(@NotNull ThemeResolver themeResolver, @Nullable String defaultThemeName) {
    myEditThemeOptions.add(CREATE_NEW_THEME);
    setThemeResolver(themeResolver, defaultThemeName);
  }

  /**
   * Sets a new theme resolver. This will trigger an update of all the elements.
   * @param themeResolver The new {@link ThemeResolver}.
   * @param defaultThemeName If not null and the model still exists, the model will try to keep this theme selected.
   */
  public void setThemeResolver(@NotNull ThemeResolver themeResolver, @Nullable String defaultThemeName) {
    // We sort the themes, displaying the local project themes at the top sorted alphabetically. The non local themes are sorted
    // alphabetically right below the project themes.
    Set<ThemeEditorStyle> temporarySet = new TreeSet<ThemeEditorStyle>(ThemeEditorUtils.STYLE_COMPARATOR);
    ImmutableList<ThemeEditorStyle> defaultThemes = ThemeEditorUtils.getDefaultThemes(themeResolver);

    Collection<ThemeEditorStyle> editableThemes = themeResolver.getLocalThemes();
    temporarySet.addAll(editableThemes);
    temporarySet.addAll(defaultThemes);
    int projectThemesCount = editableThemes.size();

    ImmutableList<ThemeEditorStyle> allThemes = ImmutableList.copyOf(temporarySet);
    ImmutableList<ThemeEditorStyle> projectThemes = allThemes.subList(0, projectThemesCount);
    ImmutableList<ThemeEditorStyle> externalThemes = allThemes.subList(projectThemesCount, allThemes.size());

    myAllItems = new SeparatedList(SEPARATOR, group(projectThemes), group(externalThemes, SHOW_ALL_THEMES),
                                   group(myEditThemeOptions));

    // Set the default selection to the first element.
    if (defaultThemeName != null && themeResolver.getTheme(defaultThemeName) != null) {
      setSelectedItem(themeResolver.getTheme(defaultThemeName));
      return;
    }

    if (!allThemes.isEmpty()) {
      setSelectedItem(allThemes.get(0));
    } else {
      setSelectedItem(null);
    }

    fireContentsChanged(this, 0, getSize() - 1);
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

      if (mySelectedObject instanceof ThemeEditorStyle) {
        if (myEditThemeOptions.size() == 2) {
          myEditThemeOptions.remove(1);
        }
        if (((ThemeEditorStyle)mySelectedObject).isProjectStyle()) {
          myEditThemeOptions.add(renameOption());
        }
      }

      fireContentsChanged(this, -1, -1);
    }
  }

  @Nullable
  @Override
  public Object getSelectedItem() {
    return mySelectedObject;
  }

  @NotNull
  private String renameOption() {
    assert mySelectedObject instanceof ThemeEditorStyle;
    ThemeEditorStyle theme = (ThemeEditorStyle)mySelectedObject;
    assert theme.isProjectStyle();
    return RENAME + theme.getSimpleName();
  }
}
