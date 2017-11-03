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

import com.android.tools.adtui.validation.Validator;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Static utility methods useful across wizards
 */
public final class WizardUtils {
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

    if (parent == null) {
      String child = ApplicationNamesInfo.getInstance().getFullProductName().replace(" ", "") + "Projects";
      return new File(SystemProperties.getUserHome(), child);
    }
    else {
      return new File(parent.replace('/', File.separatorChar));
    }
  }

  @Nullable
  public static String validatePackageName(@Nullable String packageName) {
    if (packageName.length() >= PACKAGE_LENGTH_LIMIT) {
      return AndroidBundle.message("android.wizard.module.package.too.long");
    }
    packageName = (packageName == null) ? "" : packageName;
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
   * Utility method that returns a unique name using an initial seed and a {@link Validator}
   * @return The supplied initialValue if its valid (as per the return of {@link Validator}, or the initialValue contatenated with an
   * {@link Integer} value that will be valid.
   */
  public static String getUniqueName(String initialValue, Validator<String> validator) {
    int i = 2;
    String uniqueName = initialValue;
    while (i <= 100 && validator.validate(uniqueName) != Validator.Result.OK) {
      uniqueName = initialValue + Integer.toString(i);
      i++;
    }

    return uniqueName;
  }
}
