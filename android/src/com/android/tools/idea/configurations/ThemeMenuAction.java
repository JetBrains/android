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
import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.ImmutableSet;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ThemeMenuAction extends DropDownAction {

  private final ConfigurationHolder myRenderContext;
  /**
   * May be null if the given configuration does not exist.
   */
  @Nullable private ThemeResolver myThemeResolver;

  public ThemeMenuAction(@NotNull ConfigurationHolder renderContext) {
    super("", "Theme in Editor", StudioIcons.LayoutEditor.Toolbar.THEME_BUTTON);
    myRenderContext = renderContext;

    Configuration conf = myRenderContext.getConfiguration();
    if (conf != null) {
      myThemeResolver = new ThemeResolver(myRenderContext.getConfiguration());
    }
  }

  @Override
  public void update(AnActionEvent e) {
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

      // The tests only have access to the template presentation and not the actual presentation of the
      // ActionButtonWithText that is create for this action
      // This is a little hack since the text displayed is taken from the a Presentation that might no be the same as template one.
      // The order is also important. If the text is set on the template presentation after the current presentation,
      // the button disappear (Intellij Actions magic)
      getTemplatePresentation().setText(brief, false);
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
    theme = ResourceHelper.styleToTheme(theme);

    if (brief) {
      int index = theme.lastIndexOf('.');
      if (index < theme.length() - 1) {
        return theme.substring(index + 1);
      }
    }
    return theme;
  }

  @Override
  protected boolean updateActions() {
    removeAll();
    addThemeActions();
    add(new MoreThemesAction());
    return true;
  }

  private void addThemeActions() {
    if (myThemeResolver == null) {
      return;
    }

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
    Set<String> excludedThemes = defaultTheme != null ? ImmutableSet.of(defaultTheme) : Collections.EMPTY_SET;
    List<String> projectThemeWithoutDefaultTheme = ThemeUtils.getProjectThemes(myThemeResolver, excludedThemes);
    addThemes(projectThemeWithoutDefaultTheme, currentThemeName, false);

    // Add recommended themes.
    List<String> recommendedThemes = ThemeUtils.getRecommendedThemes(myThemeResolver, excludedThemes);
    addThemes(recommendedThemes, currentThemeName, true);
  }

  /**
   * Get the current theme name of the current configuration.<br>
   * It is the theme of current configuration in layout editor, may be different than the applied theme at runtime.<br>
   * The returned name does **not** have resource prefix.<br>
   * If the current configuration or its theme doesn't exist, return null instead.
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
    if (theme != null) {
      theme = convertToNonResourcePrefixName(theme);
    }
    return theme;
  }

  /**
   * Get the default theme which will be applied at runtime.
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

    public SetThemeAction(@NotNull final ConfigurationHolder configurationHolder,
                          @NotNull final String theme,
                          @NotNull final String themeDisplayName,
                          final boolean selected) {
      super(configurationHolder, themeDisplayName);
      myTheme = theme;
      if (selected) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    protected void updatePresentation(@NotNull Presentation presentation) {
      ThemeMenuAction.this.updatePresentation(presentation);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      // The theme in here must be one of default theme, project themes, or recommend themes.
      configuration.setTheme(myTheme);
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
          }
        }
      }
    }
  }
}
