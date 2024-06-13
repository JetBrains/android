import com.android.tools.asdriver.tests.AndroidStudioInstallation
import com.android.tools.asdriver.tests.Display
import com.android.tools.asdriver.tests.TestFileSystem
import com.google.common.truth.Truth.assertThat
import org.apache.commons.lang3.SystemUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/*
 * Copyright (C) 2023 The Android Open Source Project
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
class StartUpSafeModeTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun startUpSafeModeTest() {
    val fileSystem = TestFileSystem(tempFolder.root.toPath())
    val install = AndroidStudioInstallation.fromZip(fileSystem)

    val display = Display.createDefault()
    val studio = install.runInSafeMode(display)

    val version = studio.version()
    assertThat(version).startsWith("Android Studio")
    assertThat(version).doesNotContain("dev")
    assertThat(studio.getSystemProperty("java.home")).isEqualTo(install.studioDir.resolve("jbr").toString())

    val matcher = install.ideaLog.waitForMatchingLine(".*PluginManager - Loaded bundled plugins:(.*)", 10, TimeUnit.SECONDS)
    val plugins = matcher.group(1).split(",").map { it.replace(" (.*) \\(.*\\)".toRegex(), "$1").trim() }

    val expectedPlugins = mutableListOf(
      "Android",
      "Android APK Support",
      "Android Design Tools",
      "Android SDK Upgrade Assistant",
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
      "Visual Studio Keymap",
      "WebP Support",
      "YAML"
    )

    if (SystemUtils.IS_OS_LINUX) {
      expectedPlugins.add("Emoji Picker")
    }

    assertThat(plugins).containsExactlyElementsIn(expectedPlugins)

    install.verify()
  }
}