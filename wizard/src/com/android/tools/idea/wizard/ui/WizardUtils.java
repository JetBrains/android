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
package com.android.tools.idea.wizard.ui;

import static com.intellij.util.ui.JBUI.Borders.empty;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import com.intellij.ide.GeneralLocalSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import java.io.File;
import javax.swing.JPanel;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

/**
 * Static utility methods useful across wizards
 */
public final class WizardUtils {
  public enum WIZARD_BORDER {
    EMPTY(empty()),
    SMALL(empty(16)),
    LARGE(empty(0, 72));

    public final Border border;
    WIZARD_BORDER(@NotNull Border border) {
      this.border = border;
    }
  }

  /**
   * Returns the parent directory which the last project was created into or a reasonable default
   * if this will be the first project a user has created. Either way, the {@link File} returned
   * should be a strong candidate for a home for the next project.
   */
  @NotNull
  public static File getProjectLocationParent() {
    String parent = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    if (parent != null) {
      return new File(PathUtil.toSystemDependentName(parent));
    }

    String defaultProjectLocation = GeneralLocalSettings.getInstance().getDefaultProjectDirectory();
    if (!defaultProjectLocation.isEmpty()) {
      return new File(defaultProjectLocation);
    }

    String child = ApplicationNamesInfo.getInstance().getFullProductName().replace(" ", "") + "Projects";
    return new File(SystemProperties.getUserHome(), child);
  }

  /**
   * Wrap a target string with {@code <html></html>} if it's not already so wrapped, and replaces NewLines with html breaks. This is useful
   * as various Swing components (particularly labels) act slightly differently with html input.
   */
  @NotNull
  public static String toHtmlString(@NotNull String text) {
    if (!StringUtil.isEmpty(text) && !text.startsWith("<html>")) {
      text = text.trim().replaceAll("\n", "<br>");
      return String.format("<html>%1$s</html>", text);
    }
    return text;
  }

  /**
   * When creating a WizardStepPanel which may be so tall as to require vertical scrolling,
   * using this helper method to automatically wrap it with an appropriate JScrollPane.
   */
  @NotNull
  public static JBScrollPane wrapWithVScroll(@NotNull JPanel innerPanel) {
    return wrapWithVScroll(innerPanel, WIZARD_BORDER.LARGE);
  }

  /**
   * When creating a WizardStepPanel which may be so tall as to require vertical scrolling,
   * using this helper method to automatically wrap it with an appropriate JScrollPane.
   */
  @NotNull
  public static JBScrollPane wrapWithVScroll(@NotNull JPanel innerPanel, WIZARD_BORDER wizardBorder) {
    JBScrollPane sp = new JBScrollPane(innerPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    sp.setBorder(WIZARD_BORDER.EMPTY.border); // Remove outer border line decoration
    innerPanel.setBorder(wizardBorder.border);
    return sp;
  }
}
