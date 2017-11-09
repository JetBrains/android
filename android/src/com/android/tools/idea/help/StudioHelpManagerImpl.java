/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.help;

import com.android.annotations.VisibleForTesting;
import com.intellij.help.impl.HelpManagerImpl;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Extension to the IJ help system that allows Studio to open d.a.c URLs via an IJ help topic.
 */

public class StudioHelpManagerImpl extends HelpManagerImpl {
  /**
   * Identifier prefix for studio help topic IDs. The remainder of the topic should be the URL under developer.android.com.
   */
  public static String STUDIO_HELP_PREFIX = "android-studio:";

  public static Browser myDefaultBrowser = url -> BrowserUtil.browse(url);

  /**
   * The domain for studio help topics.
   */
  public static String STUDIO_HELP_URL = "https://developer.android.com/";

  /**
   * extension for redirecting other helpIds
   */
  public static String REDIRECT_URL_EXTENSION = "r/studio-help/";

  interface Browser {
    void browse(String url);
  }

  private Browser myBrowser = myDefaultBrowser;

  @TestOnly
  void setBrowser(@NotNull Browser browser) {
    myBrowser = browser;
  }

  @Override
  public void invokeHelp(@Nullable @NonNls String id) {
    if (id == null) return;

    if (id.startsWith(STUDIO_HELP_PREFIX)) {
      openStudioHelpPage(id.substring(STUDIO_HELP_PREFIX.length()));
      return;
    }

    // The run debug configuration help topic is set deep in the platform (see SingleConfigurationConfigurable)
    if (id.startsWith("reference.dialogs.rundebug.Android")) {
      openStudioHelpPage("/r/studio-ui/rundebugconfig.html");
      return;
    }

    // open to https://developer.android.com/r/studio-help/ for all other helpIds. Including Intellij ones so map the version and redirect
    // see b/63761943
    if (PlatformUtils.isAndroidStudio()) {
      openStudioHelpPage(REDIRECT_URL_EXTENSION + getVersion() + "/?" + id);
    }
    else {
      super.invokeHelp(id);
    }
  }

  private void openStudioHelpPage(@NotNull @NonNls String urlPath) {
    myBrowser.browse(STUDIO_HELP_URL + urlPath);
  }

  @VisibleForTesting
  String getVersion() {
    ApplicationInfoEx info = ApplicationInfoEx.getInstanceEx();
    String minorVersion = info.getMinorVersion();
    int dot = minorVersion.indexOf('.');
    if (dot != -1) {
      minorVersion = minorVersion.substring(0, dot);
    }
    return info.getMajorVersion() + "." + minorVersion;
  }
}
