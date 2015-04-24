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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ThemesListModel extends AbstractListModel implements ComboBoxModel {
  private static final Set<String> DEFAULT_THEMES = ImmutableSet
    .of("Theme.AppCompat.NoActionBar",
        "Theme.AppCompat.Light.NoActionBar");
  private static final Set<String> DEFAULT_THEMES_FALLBACK = ImmutableSet
    .of("Theme.Material.NoActionBar",
        "Theme.Material.Light.NoActionBar");

  private static final JSeparator SEPARATOR = new JSeparator(SwingConstants.HORIZONTAL);

  public static final Comparator<ThemeEditorStyle> STYLE_COMPARATOR = new Comparator<ThemeEditorStyle>() {
    @Override
    public int compare(ThemeEditorStyle o1, ThemeEditorStyle o2) {
      if (o1.isProjectStyle() == o2.isProjectStyle()) {
        return o1.getName().compareTo(o2.getName());
      }

      return o1.isProjectStyle() ? -1 : 1;
    }
  };
  public static final String CREATE_NEW_THEME = "Create New Theme";
  public static final String SHOW_ALL_THEMES = "Show all themes";
  private static final Object[] EXTRA_OPTIONS = {SHOW_ALL_THEMES, SEPARATOR, CREATE_NEW_THEME};

  private ImmutableList<ThemeEditorStyle> myThemeList;
  private Object mySelectedObject;
  private int myNumberProjectThemes;
  private int mySizeBeforeExtraOptions;

  public ThemesListModel(@NotNull ThemeResolver themeResolver) {
    this(themeResolver, null);
  }

  public ThemesListModel(@NotNull ThemeResolver themeResolver, @Nullable String defaultThemeName) {
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
    Set<ThemeEditorStyle> temporarySet = new TreeSet<ThemeEditorStyle>(STYLE_COMPARATOR);

    Collection<ThemeEditorStyle> editableThemes = themeResolver.getLocalThemes();
    temporarySet.addAll(editableThemes);
    myNumberProjectThemes = editableThemes.size();

    Collection<ThemeEditorStyle> readOnlyLibThemes = new HashSet<ThemeEditorStyle>(themeResolver.getProjectThemes());
    readOnlyLibThemes.removeAll(editableThemes);

    Collection<ThemeEditorStyle> foundThemes = findThemes(readOnlyLibThemes, DEFAULT_THEMES);

    if (foundThemes.isEmpty()) {
      Collection<ThemeEditorStyle> readOnlyFrameworkThemes = themeResolver.getFrameworkThemes();
      foundThemes = findThemes(readOnlyFrameworkThemes, DEFAULT_THEMES_FALLBACK);
      if (foundThemes.isEmpty()) {
        temporarySet.addAll(readOnlyLibThemes);
        temporarySet.addAll(readOnlyFrameworkThemes);
      }
      else {
        temporarySet.addAll(foundThemes);
      }
    }
    else {
      temporarySet.addAll(foundThemes);
    }

    myThemeList = ImmutableList.copyOf(temporarySet);
    mySizeBeforeExtraOptions = myThemeList.size();
    if (myNumberProjectThemes > 0) {
      mySizeBeforeExtraOptions++;
    }

    // Set the default selection to the first element.
    if (defaultThemeName != null && themeResolver.getTheme(defaultThemeName) != null) {
      setSelectedItem(themeResolver.getTheme(defaultThemeName));
      return;
    }

    if (!myThemeList.isEmpty()) {
      setSelectedItem(myThemeList.get(0));
    } else {
      setSelectedItem(null);
    }

    fireContentsChanged(this, 0, getSize() - 1);
  }

  private @NotNull Collection<ThemeEditorStyle> findThemes(@NotNull Collection<ThemeEditorStyle> themes, final @NotNull Set<String> names) {
    return ImmutableSet.copyOf(Iterables.filter(themes, new Predicate<ThemeEditorStyle>() {
      @Override
      public boolean apply(@NotNull ThemeEditorStyle theme) {
        return names.contains(theme.getSimpleName());
      }
    }));
  }

  @Override
  public int getSize() {
    return mySizeBeforeExtraOptions + EXTRA_OPTIONS.length;
  }

  @NotNull
  @Override
  public Object getElementAt(int index) {
    if (index >= mySizeBeforeExtraOptions) {
      return EXTRA_OPTIONS[index - mySizeBeforeExtraOptions];
    }
    if (myNumberProjectThemes > 0) {
      if (index == myNumberProjectThemes) {
        return SEPARATOR;
      }
      else if (index > myNumberProjectThemes) {
        return myThemeList.get(index - 1);
      }
    }
    return myThemeList.get(index);
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
