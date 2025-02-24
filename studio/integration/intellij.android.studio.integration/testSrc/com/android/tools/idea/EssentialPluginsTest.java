/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.Display;
import com.android.tools.asdriver.tests.TestFileSystem;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Ensures Studio can launch even when all plugins are disabled---leaving behind only the plugins marked "essential"
 * in AndroidStudioApplicationInfo.xml. For background see b/202048599, b/365493089, and IJPL-6075.
 */
public class EssentialPluginsTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void essentialPluginsTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());
    AndroidStudioInstallation install = AndroidStudioInstallation.fromZip(fileSystem);
    install.addVmOption("-Didea.is.internal=true"); // Allows executing internal actions in the test.

    // Force-retain certain plugins needed for testing (i.e., Android Studio Driver and its dependencies).
    // This VM arg is parsed by com.intellij.ide.plugins.DisabledPluginsState.
    var pluginsForTesting = List.of("com.android.tools.asdriver", "com.jetbrains.performancePlugin");
    install.addVmOption("-Didea.required.plugins.id=" + String.join(",", pluginsForTesting));

    try (Display display = Display.createDefault()) {
      int initialPluginCount;
      try (AndroidStudio studio = install.run(display)) {
        initialPluginCount = countEnabledPlugins(install);
        studio.executeAction("Android.ValidateEssentialPlugins"); // Reports common issues (with polished error messages).
        studio.executeAction("Android.DisableAllPlugins");
        install.getIdeaLog().waitForMatchingLine(".*DisableAllPluginsAction - Disabled all plugins", 10, TimeUnit.SECONDS);
      }
      // Check that Studio can still launch after all non-essential plugins have been disabled.
      try (AndroidStudio ignored = install.run(display)) {
        int pluginCount = countEnabledPlugins(install);
        assertThat(pluginCount).isLessThan(initialPluginCount);
        assertThat(pluginCount).isGreaterThan(pluginsForTesting.size());
      }
    }
  }

  private int countEnabledPlugins(AndroidStudioInstallation install) throws Exception {
    Matcher matcher = install.getIdeaLog().waitForMatchingLine(".*PluginManager - Loaded bundled plugins:(.*)", 10, TimeUnit.SECONDS);
    return matcher.group(1).split(",").length;
  }
}
