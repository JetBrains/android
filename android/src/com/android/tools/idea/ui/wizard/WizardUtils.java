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
package com.android.tools.idea.ui.wizard;

import static com.intellij.util.ui.JBUI.Borders.empty;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.wizard.template.Category;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.JPanel;
import javax.swing.border.Border;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static utility methods useful across wizards
 */
public final class WizardUtils {
  // TODO: parentej needs to be updated to 4.0.0 when released
  public static final String COMPOSE_MIN_AGP_VERSION = "4.0.0-alpha02";

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
   * The package is used to create a directory (eg: MyApplication/app/src/main/java/src/my/package/name)
   * A windows directory path cannot be longer than 250 chars
   * On unix/mac a directory name cannot be longer than 250 chars
   * On all platforms, aapt fails with really cryptic errors if the package name is longer that ~200 chars
   * Having a sane length for the package also seems a good thing
   */
  private static final int PACKAGE_LENGTH_LIMIT = 100;

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

    String defaultProjectLocation = GeneralSettings.getInstance().getDefaultProjectDirectory();
    if (defaultProjectLocation != null && !defaultProjectLocation.isEmpty()) {
      return new File(defaultProjectLocation);
    }

    String child = ApplicationNamesInfo.getInstance().getFullProductName().replace(" ", "") + "Projects";
    return new File(SystemProperties.getUserHome(), child);
  }

  @Nullable
  public static String validatePackageName(@Nullable String packageName) {
    packageName = (packageName == null) ? "" : packageName;
    if (packageName.length() >= PACKAGE_LENGTH_LIMIT) {
      return AndroidBundle.message("android.wizard.module.package.too.long");
    }
    return AndroidUtils.validateAndroidPackageName(packageName);
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
   * Utility method used to create a URL from its String representation without throwing a {@link MalformedURLException}.
   * Callers should use this if they're absolutely certain their URL is well formatted.
   */
  @NotNull
  public static URL toUrl(@NotNull String urlAsString) {
    URL url;
    try {
      url = new URL(urlAsString);
    }
    catch (MalformedURLException e) {
      // Caller should guarantee this will never happen!
      throw new RuntimeException(e);
    }
    return url;
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

  /**
   * Utility method that returns a unique name using an initial seed and a {@link Validator}
   * @return The supplied initialValue if its valid (as per the return of {@link Validator}, or the initialValue contatenated with an
   * {@link Integer} value that will be valid.
   */
  public static String getUniqueName(String initialValue, Validator<? super String> validator) {
    int i = 2;
    String uniqueName = initialValue;
    while (i <= 100 && validator.validate(uniqueName).getSeverity() == Validator.Severity.ERROR) {
      uniqueName = initialValue + i;
      i++;
    }

    return uniqueName;
  }

  public static boolean hasComposeMinAgpVersion(@Nullable Project project, Category category) {
    if (project == null || !Category.Compose.equals(category)) {
      return true;
    }
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromModel(project);
    if (androidPluginInfo == null) {
      return true;
    }
    GradleVersion agpVersion = androidPluginInfo.getPluginVersion();
    if (agpVersion == null) {
      return true;
    }
    return agpVersion.compareTo(COMPOSE_MIN_AGP_VERSION) >= 0;
  }
}
