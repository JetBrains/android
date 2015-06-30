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

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import java.util.ArrayList;
import java.util.List;
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
  private Module mySelectedModule;
  private final List<String> myEditOptions;

  public ThemesListModel(@NotNull Project project, @NotNull ImmutableList<ThemeEditorStyle> defaultThemes, @Nullable ThemeEditorStyle defaultTheme) {
    ImmutableList<ProjectThemeResolver.ThemeWithSource> editableThemes = ProjectThemeResolver.getEditableProjectThemes(project);

    // We sort the themes, displaying the local project themes at the top sorted alphabetically. The non local themes are sorted
    // alphabetically right below the project themes.
    Set<ThemeEditorStyle> temporarySet = new TreeSet<ThemeEditorStyle>(ThemeEditorUtils.STYLE_COMPARATOR);
    for (ProjectThemeResolver.ThemeWithSource theme : editableThemes) {
      temporarySet.add(theme.getTheme());
    }
    temporarySet.addAll(defaultThemes);

    ImmutableList<ThemeEditorStyle> allThemes = ImmutableList.copyOf(temporarySet);
    ImmutableList<ThemeEditorStyle> externalThemes = allThemes.subList(editableThemes.size(), allThemes.size());

    Object selectedItem = null;
    if (defaultTheme != null) {
      selectedItem = defaultTheme;
    }
    else if (!allThemes.isEmpty()) {
      selectedItem = allThemes.get(0);
    }

    myEditOptions = new ArrayList<String>();
    buildEditOptionsList(selectedItem);

    myAllItems = new SeparatedList(SEPARATOR, group(editableThemes), group(externalThemes, SHOW_ALL_THEMES),
                                   group(myEditOptions));

    // Set the default selection to the first element.
    setSelectedItem(selectedItem);
  }

  private void buildEditOptionsList(@Nullable Object selectedItem) {
    myEditOptions.clear();
    myEditOptions.add(CREATE_NEW_THEME);
    ThemeEditorStyle selectedTheme = getStyle(selectedItem);
    if (selectedTheme != null && selectedTheme.isProjectStyle()) {
      myEditOptions.add(RENAME + selectedTheme.getName());
    }
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

  @Nullable
  public static ThemeEditorStyle getStyle(final @Nullable Object object) {
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

    mySelectedObject = anItem;
    if (anItem instanceof ProjectThemeResolver.ThemeWithSource) {
      mySelectedModule = ((ProjectThemeResolver.ThemeWithSource)anItem).getSourceModule();
    }
    buildEditOptionsList(mySelectedObject);

    fireContentsChanged(this, -1, -1);
  }

  @Nullable
  @Override
  public Object getSelectedItem() {
    return mySelectedObject;
  }

  @Nullable
  public Module getSelectedModule() {
    return mySelectedModule;
  }
}
