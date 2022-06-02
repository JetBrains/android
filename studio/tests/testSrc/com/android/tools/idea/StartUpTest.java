/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;

import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.Display;
import com.android.tools.asdriver.tests.XvfbServer;
import java.util.Arrays;
import java.util.regex.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StartUpTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void startUpTest() throws Exception {
    AndroidStudioInstallation install = AndroidStudioInstallation.fromZip(tempFolder);
    try (Display display = new XvfbServer();
         AndroidStudio studio = install.run(display)) {
      // Wait for plugin manager to load all plugins
      Matcher matcher = studio.waitForLog(".*PluginManager - Loaded bundled plugins:(.*)", 10000);
      String[] plugins = matcher.group(1).split(",");
      for (int i = 0; i < plugins.length; i++) {
        plugins[i] = plugins[i].replaceAll(" (.*) \\(.*\\)", "$1").strip();
      }
      Arrays.sort(plugins);
      assertArrayEquals(new String[]{
        "Android",
        "Android APK Support",
        "Android NDK Support",
        "App Links Assistant",
        "C/C++ Language Support",
        "CIDR Base",
        "CIDR Debugger",
        "ChangeReminder",
        "Clangd Support",
        "Clangd-CLion Bridge",
        "Code Coverage for Java",
        "Configuration Script",
        "Copyright",
        "Design Tools",
        "EditorConfig",
        "Emoji Picker",
        "Firebase App Indexing",
        "Firebase Services",
        "Firebase Testing",
        "Git",
        "GitHub",
        "Google Cloud Tools Core",
        "Google Cloud Tools For Android Studio",
        "Google Developers Samples",
        "Google Login",
        "Gradle",
        "Gradle-Java",
        "Groovy",
        "IDEA CORE",
        "Images",
        "IntelliLang",
        "JUnit",
        "Java",
        "Java Bytecode Decompiler",
        "Java IDE Customization",
        "Java Internationalization",
        "Java Stream Debugger",
        "JetBrains Repository Search",
        "JetBrains maven model api classes",
        "Jetpack Compose",
        "Kotlin",
        "Machine Learning Code Completion",
        "Machine Learning Code Completion Models",
        "Machine Learning Local Models",
        "Markdown",
        "Mercurial",
        "Properties",
        "Refactoring Detector",
        "Settings Repository",
        "Settings Sync",
        "Shell Script",
        "Smali Support",
        "Subversion",
        "Task Management",
        "Terminal",
        "Test Recorder",
        "TestNG",
        "TextMate Bundles",
        "Toml",
        "WebP Support",
        "YAML",
      }, plugins);
      studio.kill(0);
    }
  }
}
