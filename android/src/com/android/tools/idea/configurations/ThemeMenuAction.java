/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.android.SdkConstants;
import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThemeMenuAction extends DropDownAction {

  private final ConfigurationHolder myRenderContext;

  public ThemeMenuAction(@NotNull ConfigurationHolder renderContext) {
    super("Theme for Preview", "Theme for Preview", StudioIcons.LayoutEditor.Toolbar.THEME_BUTTON);
    myRenderContext = renderContext;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myRenderContext.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      String brief = getThemeLabel(configuration.getTheme(), true);
      presentation.setText(brief, false);
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  /**
   * Returns a suitable label to use to display the given theme
   *
   * @param theme the theme to produce a label for
   * @param brief if true, generate a brief label (suitable for a toolbar
   *              button), otherwise a fuller name (suitable for a menu item)
   * @return the label
   */
  @NotNull
  public static String getThemeLabel(@Nullable String theme, boolean brief) {
    if (theme == null) {
      return "";
    }
    theme = IdeResourcesUtil.styleToTheme(theme);

    if (brief) {
      int index = theme.lastIndexOf('.');
      if (index < theme.length() - 1) {
        return theme.substring(index + 1);
      }
    }
    return theme;
  }

  @Override
  protected boolean updateActions(@NotNull DataContext context) {
    removeAll();
    addThemeActions();
    add(new MoreThemesAction());
    return true;
  }

  private void addThemeActions() {
    Configuration conf = myRenderContext.getConfiguration();
    if (conf == null) {
      return;
    }
    ThemeResolver themeResolver = new ThemeResolver(myRenderContext.getConfiguration());
    StyleResourceValue[] baseThemes = themeResolver.requiredBaseThemes();

    // This is the selected theme in layout editor, may be different with the theme used at runtime.
    String currentThemeName = getCurrentTheme();

    // Add the default theme, which is the theme applied at runtime.
    String defaultTheme = getDefaultTheme();
    if (defaultTheme != null) {
      String themeName = ThemeUtils.getPreferredThemeName(defaultTheme);
      add(new SetThemeAction(myRenderContext, themeName, themeName + " [default]", isSameTheme(defaultTheme, currentThemeName)));
      addSeparator();
    }

    // Add project themes exclude the default theme.
    Set<String> excludedThemes = defaultTheme != null ? ImmutableSet.of(defaultTheme) : ImmutableSet.of();
    Function1<ConfiguredThemeEditorStyle, Boolean> filter = ThemeUtils.createFilter(themeResolver, excludedThemes, baseThemes);
    List<String> projectThemeWithoutDefaultTheme = ThemeUtils.getProjectThemeNames(themeResolver, filter);
    addThemes(projectThemeWithoutDefaultTheme, currentThemeName, false);

    // Add recommended themes.
    List<String> recommendedThemes = ThemeUtils.getRecommendedThemeNames(themeResolver, filter);
    addThemes(recommendedThemes, currentThemeName, true);

    Configuration config = myRenderContext.getConfiguration();
    if (config != null) {
      Project project = config.getConfigModule().getProject();

      // Add recent used themes
      // Don't show any theme added above as recent Theme.
      Set<String> existingThemes = new HashSet<>();
      existingThemes.addAll(excludedThemes);
      existingThemes.addAll(projectThemeWithoutDefaultTheme);
      existingThemes.addAll(recommendedThemes);
      List<String> recentUsedThemes = ThemeUtils.getRecentlyUsedThemes(project, existingThemes);
      addThemes(recentUsedThemes, currentThemeName, true);
    }
  }

  /**
   * Returns the current theme name of the current configuration.
   * It is the theme of current configuration in layout editor, may be different than the applied theme at runtime.
   * The returned name does <b>not</b> have resource prefix.
   * If the current configuration or its theme doesn't exist, returns null instead.
   *
   * @return The name of current theme without resource prefix, or null if the theme doesn't exist.
   */
  @Nullable
  private String getCurrentTheme() {
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return null;
    }

    String theme = configuration.getTheme();
    theme = convertToNonResourcePrefixName(theme);
    return theme;
  }

  /**
   * Returns the default theme which will be applied at runtime.
   */
  @Nullable
  private String getDefaultTheme() {
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return null;
    }
    ConfigurationManager configurationManager = configuration.getConfigurationManager();
    String theme = configurationManager.computePreferredTheme(configuration);
    return convertToNonResourcePrefixName(theme);
  }

  private void addThemes(List<String> themes, @Nullable String currentSelectedTheme, boolean builtInTheme) {
    if (themes.isEmpty()) {
      return;
    }

    for (String theme : themes) {
      String displayName = getThemeLabel(theme);
      if (builtInTheme) {
        displayName = ThemeUtils.getPreferredThemeName(displayName);
      }
      add(new SetThemeAction(myRenderContext, theme, displayName, isSameTheme(theme, currentSelectedTheme)));
    }
    addSeparator();
  }

  @NotNull
  private static String getThemeLabel(@NotNull String theme) {
    return ResolutionUtils.getNameFromQualifiedName(theme.substring(theme.indexOf('/') + 1));
  }

  private static boolean isSameTheme(@Nullable String left, @Nullable String right) {
    if (left == null) {
      return right == null;
    }
    return right != null && convertToNonResourcePrefixName(left).equals(convertToNonResourcePrefixName(right));
  }

  /**
   * This function remove the resource prefix and rename theme to the raw resource name.<br>
   * If the name is @android:style/[resource_name], it returns android:[resource_name].<br>
   * If the name is @style/[resource_name], it returns [resource_name].
   */
  @NotNull
  private static String convertToNonResourcePrefixName(@NotNull String theme) {
    if (theme.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX)) {
      theme = theme.replace(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX, SdkConstants.PREFIX_ANDROID);
    }
    if (theme.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX)) {
      theme = theme.replace(SdkConstants.STYLE_RESOURCE_PREFIX, "");
    }
    return theme;
  }

  private class SetThemeAction extends ConfigurationAction {
    private final String myTheme;
    private final boolean mySelected;

    public SetThemeAction(@NotNull final ConfigurationHolder configurationHolder,
                          @NotNull final String theme,
                          @NotNull final String themeDisplayName,
                          final boolean selected) {
      super(configurationHolder, themeDisplayName);
      myTheme = theme;
      mySelected = selected;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      Toggleable.setSelected(presentation, mySelected);
    }

    @Override
    protected void updatePresentation(@NotNull Presentation presentation) {
      ThemeMenuAction.this.updatePresentation(presentation);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      // The theme in here must be one of default theme, project themes, recommend themes, or recent used themes.
      // It doesn't need to be added to recent used theme since it is in the dropdown menu already.
      configuration.setTheme(myTheme);
      if (ThemeUtils.getRecentlyUsedThemes(configuration.getConfigModule().getProject()).contains(myTheme)) {
        // Add this theme to recent Themes again to make it as the most recent one.
        ThemeUtils.addRecentlyUsedTheme(configuration.getConfigModule().getProject(), myTheme);
      }
    }
  }

  @VisibleForTesting
  public class MoreThemesAction extends DumbAwareAction {

    public MoreThemesAction() {
      super("More Themes...");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        ThemeSelectionDialog dialog = new ThemeSelectionDialog(configuration);
        if (dialog.showAndGet()) {
          String theme = dialog.getTheme();
          if (theme != null) {
            configuration.setTheme(theme);
            ThemeUtils.addRecentlyUsedTheme(configuration.getConfigModule().getProject(), theme);
          }
        }
      }
    }
  }
}
