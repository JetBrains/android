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
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import static com.android.tools.idea.editors.theme.SeparatedList.group;

public class ThemesListModel extends AbstractListModel implements ComboBoxModel {

  private static final JSeparator SEPARATOR = new JSeparator(SwingConstants.HORIZONTAL);

  public static final String CREATE_NEW_THEME = "Create New Theme";
  public static final String SHOW_ALL_THEMES = "Show all themes";
  public static final String RENAME = "Rename ";

  // TODO(ddrone): replace untyped list to an explicit union type
  private SeparatedList myAllItems;
  private Object mySelectedObject;
  private Module mySelectedModule;

  private final ArrayList<String> myEditThemeOptions = new ArrayList<String>();

  public ThemesListModel(@NotNull Project project, @NotNull ThemeResolver themeResolver, @Nullable String defaultThemeName) {
    myEditThemeOptions.add(CREATE_NEW_THEME);
    setThemeResolver(project, themeResolver, defaultThemeName);
  }

  /**
   * Sets a new theme resolver. This will trigger an update of all the elements.
   * @param themeResolver The new {@link ThemeResolver}.
   * @param defaultThemeName If not null and the model still exists, the model will try to keep this theme selected.
   */
  public void setThemeResolver(Project project, @NotNull ThemeResolver themeResolver, @Nullable String defaultThemeName) {
    // We sort the themes, displaying the local project themes at the top sorted alphabetically. The non local themes are sorted
    // alphabetically right below the project themes.
    ImmutableList<ThemeEditorStyle> defaultThemes = ThemeEditorUtils.getDefaultThemes(themeResolver);

    ImmutableList<ProjectThemeResolver.ThemeWithSource> editableThemes = ProjectThemeResolver.getEditableProjectThemes(project);
    Set<ThemeEditorStyle> temporarySet = new TreeSet<ThemeEditorStyle>(ThemeEditorUtils.STYLE_COMPARATOR);
    for (ProjectThemeResolver.ThemeWithSource theme : editableThemes) {
      temporarySet.add(theme.getTheme());
    }
    temporarySet.addAll(defaultThemes);

    ImmutableList<ThemeEditorStyle> allThemes = ImmutableList.copyOf(temporarySet);
    ImmutableList<ThemeEditorStyle> externalThemes = allThemes.subList(editableThemes.size(), allThemes.size());

    myAllItems = new SeparatedList(SEPARATOR, group(editableThemes), group(externalThemes, SHOW_ALL_THEMES),
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
    Object item = myAllItems.get(index);
    ThemeEditorStyle style = getStyle(item);
    return style != null ? style : item;
  }

  private static ThemeEditorStyle getStyle(final Object object) {
    if (object instanceof ThemeEditorStyle) {
      return (ThemeEditorStyle)object;
    }
    else if (object instanceof ProjectThemeResolver.ThemeWithSource) {
      return ((ProjectThemeResolver.ThemeWithSource)object).getTheme();
    }
    return null;
  }

  @Override
  public void setSelectedItem(@Nullable Object anItem) {
    if (anItem instanceof JSeparator) {
      return;
    }

    ThemeEditorStyle selectedStyle = getStyle(anItem);
    if (selectedStyle == null) {
      mySelectedObject = anItem;
    }
    else {
      mySelectedObject = selectedStyle;
      if (myEditThemeOptions.size() == 2) {
        myEditThemeOptions.remove(1);
      }
      if (selectedStyle.isProjectStyle()) {
        myEditThemeOptions.add(renameOption());
      }
      if (anItem instanceof ProjectThemeResolver.ThemeWithSource) {
        mySelectedModule = ((ProjectThemeResolver.ThemeWithSource)anItem).getSourceModule();
      }
    }

    fireContentsChanged(this, -1, -1);
  }

  @Nullable
  @Override
  public Object getSelectedItem() {
    return mySelectedObject;
  }

  @NotNull
  private String renameOption() {
    ThemeEditorStyle theme = getStyle(mySelectedObject);
    assert theme != null : "Theme should be selected to call renameOption()";
    assert theme.isProjectStyle();
    return RENAME + theme.getSimpleName();
  }

  @Nullable
  public Module getSelectedModule() {
    return mySelectedModule;
  }
}
