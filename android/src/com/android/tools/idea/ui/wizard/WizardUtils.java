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

import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Static utility methods useful across wizards
 */
public final class WizardUtils {
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

  /**
   * Wrap a target string with {@code <html></html>} if it's not already so wrapped. This is useful
   * as various Swing components (particularly labels) act slightly differently with html input.
   */
  @NotNull
  public static String toHtmlString(@NotNull String text) {
    if (!StringUtil.isEmpty(text) && !text.startsWith("<html>")) {
      return String.format("<html>%1$s</html>", text.trim());
    }
    return text;
  }
}
