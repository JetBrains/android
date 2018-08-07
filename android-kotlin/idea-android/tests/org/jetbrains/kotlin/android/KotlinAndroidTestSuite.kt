/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.kotlin.android

import com.android.testutils.JarTestSuiteRunner
import com.android.testutils.TestUtils
import com.android.tools.tests.IdeaTestSuiteBase
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(JarTestSuiteRunner::class)
@JarTestSuiteRunner.ExcludeClasses(org.jetbrains.kotlin.android.KotlinAndroidTestSuite::class)
class KotlinAndroidTestSuite : IdeaTestSuiteBase() {
  companion object {

    init {
      IdeaTestSuiteBase.symlinkToIdeaHome(
        "tools/adt/idea/android/annotations",
        "tools/adt/idea/android/lib",
        "tools/idea/build.txt")

      IdeaTestSuiteBase.symlinkToIdeaHome(
        "tools/adt/idea/android-kotlin/android-extensions-idea/testData",
        "tools/adt/idea/android-kotlin/android-extensions-jps/testData",
        "tools/adt/idea/android-kotlin/idea-android/testData")

      // The Kotlin plugin test framework code expects a 'dist' directory pointing to the Kotlin plugin.
      val distDir = File(TestUtils.getWorkspaceRoot(), "tools/adt/idea/android-kotlin/dist").toPath()
      val kotlinDir = File(TestUtils.getWorkspaceRoot(), "prebuilts/tools/common/kotlin-plugin/Kotlin").toPath()
      Files.createSymbolicLink(distDir, kotlinDir)

      // Enable Kotlin plugin (see PluginManagerCore.PROPERTY_PLUGIN_PATH).
      System.setProperty("plugin.path", TestUtils.getWorkspaceFile("prebuilts/tools/common/kotlin-plugin/Kotlin").absolutePath)
    }
  }
}
