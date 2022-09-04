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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.Display;
import com.android.tools.asdriver.tests.TestFileSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import org.apache.commons.lang.SystemUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StartUpTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void startUpTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());
    AndroidStudioInstallation install = AndroidStudioInstallation.fromZip(fileSystem);
    try (Display display = Display.createDefault();
         AndroidStudio studio = install.run(display)) {
      // Wait for plugin manager to load all plugins
      Matcher matcher = install.getIdeaLog().waitForMatchingLine(".*PluginManager - Loaded bundled plugins:(.*)", 10, TimeUnit.SECONDS);
      String[] plugins = matcher.group(1).split(",");
      for (int i = 0; i < plugins.length; i++) {
        plugins[i] = plugins[i].replaceAll(" (.*) \\(.*\\)", "$1").strip();
      }

      List<String> expectedPlugins = new ArrayList<>(Arrays.asList(
        "Android",
        "Android APK Support",
        "Android NDK Support",
        "App Links Assistant",
        "C/C++ Language Support",
        "CIDR Base",
        "CIDR Debugger",
        "Clangd Support",
        "Clangd-CLion Bridge",
        "Code Coverage for Java",
        "com.intellij.dev",
        "Configuration Script",
        "Copyright",
        "Design Tools",
        "Eclipse Keymap",
        "EditorConfig",
        "Firebase App Indexing",
        "Firebase Direct Access",
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
        "NetBeans Keymap",
        "Plugin DevKit",
        "Properties",
        "Settings Repository",
        "Settings Sync",
        "Shell Script",
        "Smali Support",
        "Subversion",
        "TargetSDKVersion Upgrade Assistant",
        "Task Management",
        "Terminal",
        "Test Recorder",
        "TestNG",
        "TextMate Bundles",
        "Toml",
        "Visual Studio Keymap",
        "WebP Support",
        "YAML"
      ));

      if (SystemUtils.IS_OS_LINUX) {
        expectedPlugins.add("Emoji Picker");
      }

      assertThat(plugins).asList().containsExactlyElementsIn(expectedPlugins);

      install.verify();
    }
  }
}
