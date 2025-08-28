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
import com.android.tools.asdriver.tests.Sherlock;
import com.android.tools.asdriver.tests.SherlockInstallation;
import com.android.tools.testlib.Display;
import com.android.tools.testlib.TestFileSystem;
import com.intellij.openapi.util.SystemInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SherlockStartUpTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void startUpTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());
    SherlockInstallation install = SherlockInstallation.fromZip(fileSystem);
    if (SystemInfo.isMac) {
      install.addVmOption("-Djava.awt.headless=true");
    }
    try (Display display = Display.createDefault();
         Sherlock sherlock = install.run(display)) {
      // Check that AndroidStudioApplicationInfo.xml was patched properly, and that it is not overridden by
      // ApplicationNamesInfo.getAppInfoData (see org.jetbrains.intellij.build.tasks.injectAppInfo and Change Id51ff2663).
      String version = sherlock.version();
      assertThat(version).startsWith("Sherlock");
      assertThat(version).doesNotContain("dev");

      String javaHome = install.getStudioDir().resolve("jbr").toString();
      // On Mac the java.home has "/Contents/Home" added to the jbr path that are not present on other installations
      if (SystemInfo.isMac) {
        assertThat(javaHome).doesNotContain("/Contents/Home");
        javaHome = install.getStudioDir().resolve("Contents/jbr/Contents/Home").toString();
      }
      assertThat(sherlock.getSystemProperty("java.home")).isEqualTo(javaHome);

      // Wait for plugin manager to load all plugins
      Matcher matcher = install.getIdeaLog().waitForMatchingLine(".*PluginManager - Loaded bundled plugins:(.*)", 10, TimeUnit.SECONDS);
      String[] plugins = matcher.group(1).split(",");
      for (int i = 0; i < plugins.length; i++) {
        plugins[i] = plugins[i].replaceAll(" (.*) \\(.*\\)", "$1").strip();
      }
      List<String> expectedPlugins = new ArrayList<>(Arrays.asList(
        "IDEA CORE"
      ));
      assertThat(plugins).asList().containsExactlyElementsIn(expectedPlugins);

      install.verify();
    }
  }
}
