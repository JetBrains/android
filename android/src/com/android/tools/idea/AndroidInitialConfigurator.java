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

package com.android.tools.idea;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

/** Customize Android IDE specific experience. */
public class AndroidInitialConfigurator {
  @NonNls
  private static final ExtensionPointName<Runnable> EP_NAME =
    ExtensionPointName.create("com.intellij.androidStudioInitializer");

  public AndroidInitialConfigurator() {
    setupSystemProperties();
    activateAndroidStudioInitializerExtensions();
  }

  /**
   * Setup some Java system properties based on environment variables.
   * This makes it easier to do local testing.
   */
  private static void setupSystemProperties() {
    // If defined, AS_UPDATE_URL should point to the *root* of the updates.xml file to use
    // and patches are expected to be in the same folder.
    String updateUrl = System.getenv("AS_UPDATE_URL");
    if (updateUrl != null) {
      if (!updateUrl.endsWith("/")) {
        updateUrl += "/";
      }
      // Set the Java system properties expected by UpdateChecker.
      System.setProperty("idea.updates.url", updateUrl + "updates.xml");
      System.setProperty("idea.patches.url", updateUrl);
    }
  }

  private static void activateAndroidStudioInitializerExtensions() {
    for (Runnable r : EP_NAME.getExtensionList()) {
      r.run();
    }
  }
}
