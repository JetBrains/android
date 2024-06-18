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
import com.intellij.openapi.util.SystemInfoRt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
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
      // Check that AndroidStudioApplicationInfo.xml was patched properly, and that it is not overridden by
      // ApplicationNamesInfo.getAppInfoData (see org.jetbrains.intellij.build.tasks.injectAppInfo and Change Id51ff2663).
      String version = studio.version();
      assertThat(version).startsWith("Android Studio");
      assertThat(version).doesNotContain("dev");
      assertThat(studio.getSystemProperty("java.home")).isEqualTo(install.getStudioDir().resolve("jbr").toString());

      // Wait for plugin manager to load all plugins
      Matcher matcher = install.getIdeaLog().waitForMatchingLine(".*PluginManager - Loaded bundled plugins:(.*)", 10, TimeUnit.SECONDS);
      String[] plugins = matcher.group(1).split(",");
      for (int i = 0; i < plugins.length; i++) {
        plugins[i] = plugins[i].replaceAll(" (.*) \\(.*\\)", "$1").strip();
      }

      List<String> expectedPlugins = new ArrayList<>(Arrays.asList(
        "Android",
        "Android APK Support",
        "Android Design Tools",
        "Android SDK Upgrade Assistant",
        "Android Studio Driver",
        "Android NDK Support",
        "App Links Assistant",
        "C/C++ Language Support via Classic Engine",
        "CIDR Base",
        "CIDR Debugger",
        "ClangConfig",
        "Clangd Support",
        "Clangd-CLion Bridge",
        "ClangFormat",
        "Code Coverage for Java",
        "com.intellij.dev",
        "Configuration Script",
        "Copyright",
        "Eclipse Keymap",
        "EditorConfig",
        "Device Streaming",
        "Firebase Services",
        "Firebase Testing",
        "Gemini",
        "Git for App Insights",
        "Git",
        "GitHub",
        "GitLab",
        "Google Cloud Tools For Android Studio",
        "Gradle",
        "Gradle-Java",
        "Groovy",
        "HTML Tools",
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
        "Markdown",
        "Maven server api classes",
        "Mercurial",
        "NetBeans Keymap",
        "Performance Testing",
        "Plugin DevKit",
        "Properties",
        "Relational Dataflow Analysis",
        "Shell Script",
        "Smali Support",
        "Subversion",
        "Task Management",
        "Terminal",
        "Test Recorder",
        "TestNG",
        "TextMate Bundles",
        "Toml",
        "Turbo Complete",
        "Visual Studio Keymap",
        "WebP Support",
        "YAML"
      ));

      if (SystemInfoRt.isLinux) {
        expectedPlugins.add("Emoji Picker");
      }

      assertThat(plugins).asList().containsExactlyElementsIn(expectedPlugins);

      install.verify();
    }
  }
}
