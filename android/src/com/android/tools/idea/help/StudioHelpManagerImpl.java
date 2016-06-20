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

import com.intellij.help.impl.HelpManagerImpl;
import com.intellij.ide.BrowserUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Extension to the IJ help system that allows Studio to open d.a.c URLs via an IJ help topic.
 */

public class StudioHelpManagerImpl extends HelpManagerImpl {
  /**
   * Identifier prefix for studio help topic IDs. The remainder of the topic should be the URL under developer.android.com.
   */
  public static String STUDIO_HELP_PREFIX = "android-studio:";

  /**
   * The domain for studio help topics.
   */
  private static String STUDIO_HELP_URL = "https://developer.android.com/";

  @Override
  public void invokeHelp(@Nullable @NonNls String id) {
    if (id != null && id.startsWith(STUDIO_HELP_PREFIX)) {
      try {
        URL url = new URL(STUDIO_HELP_URL + id.substring(STUDIO_HELP_PREFIX.length()));
        BrowserUtil.browse(url);
        return;
      }
      catch (MalformedURLException ignore) {
        // nothing
      }
    }

    super.invokeHelp(id);
  }
}
