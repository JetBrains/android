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

import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ThemeMenuAction extends FlatComboAction {
  private final RenderContext myRenderContext;

  public ThemeMenuAction(@NotNull RenderContext renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Theme to render layout with");
    presentation.setIcon(AndroidIcons.Themes);
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myRenderContext.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      String brief = getThemeLabel(configuration.getTheme(), true);
      presentation.setText(brief);
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
   *            button), otherwise a fuller name (suitable for a menu item)
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
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);

    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return group;
    }
    String current = configuration.getTheme();
    ConfigurationManager manager = configuration.getConfigurationManager();
    // TODO: Preferred theme
    // TODO: Manifest themes
    // TODO: Split up by theme category (light, dark, etc)
    List<String> projectThemes = manager.getProjectThemes();
    List<String> frameworkThemes = manager.getFrameworkThemes(configuration.getTarget());
    if (!projectThemes.isEmpty()) {
      for (String theme : projectThemes) {
        group.add(new SetThemeAction(theme, theme.equals(current)));
      }
      if (!frameworkThemes.isEmpty()) {
        group.addSeparator();
      }
    }
    if (!frameworkThemes.isEmpty()) {
      for (String theme : frameworkThemes) {
        group.add(new SetThemeAction(theme, theme.equals(current)));
      }
    }

    return group;
  }

  private class SetThemeAction extends AnAction {
    private final String myTheme;

    public SetThemeAction(@NotNull String theme, boolean select) {
      super(getThemeLabel(theme, false));
      myTheme = theme;
      if (select) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        configuration.setTheme(myTheme);
      }
    }
  }
}
