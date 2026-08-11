/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine.ui

import com.android.tools.idea.testing.ResolvedAgpVersionSoftwareEnvironment
import java.io.File

object TemplateEngineTestUtils {
  /**
   * Patches generated project build files and sources so that they compile and assemble offline across different AGP (8.x and 9.0+) and
   * Kotlin Gradle Plugin versions in tests.
   */
  fun patchAdditionalVersions(projectRoot: File, resolvedAgp: ResolvedAgpVersionSoftwareEnvironment) {
    val appBuildGradle = File(projectRoot, "app/build.gradle.kts")
    val isAgp9 = isAgp9OrHigher(resolvedAgp.agpVersion)
    val kotlinVersion = resolvedAgp.kotlinVersion

    if (appBuildGradle.exists()) {
      var content = appBuildGradle.readText()
      content = content.replace(Regex("""alias\(libs\.plugins\.kotlin\.serialization\)"""), "")

      if (isAgp9) {
        if (content.contains("id(\"org.jetbrains.kotlin.android\")")) {
          content = content.replace("id(\"org.jetbrains.kotlin.android\")", "id(\"org.jetbrains.kotlin.plugin.compose\")")
        } else if (!content.contains("id(\"org.jetbrains.kotlin.plugin.compose\")")) {
          content =
            content.replace(
              "id(\"com.android.application\")",
              "id(\"com.android.application\")\n    id(\"org.jetbrains.kotlin.plugin.compose\")",
            )
        }
        content = content.replace(Regex("""kotlinOptions\s*\{[\s\S]*?\}"""), "")
        content = content.replace(Regex("""composeOptions\s*\{[\s\S]*?\}"""), "")
      } else {
        if (content.contains("id(\"org.jetbrains.kotlin.android\")")) {
          content =
            content.replace(
              "id(\"org.jetbrains.kotlin.android\")",
              "id(\"org.jetbrains.kotlin.android\")\n    id(\"org.jetbrains.kotlin.plugin.compose\")",
            )
        } else {
          content =
            content.replace(
              "id(\"com.android.application\")",
              "id(\"com.android.application\")\n    id(\"org.jetbrains.kotlin.plugin.compose\")",
            )
        }
      }

      content = content.replace(Regex("""kotlin\s*\{\s*jvmToolchain\(\s*17\s*\)\s*\}"""), "")
      content = content.replace(Regex("""compileSdk(Version)?\s*(=\s*)?"?(android-)?\d+"?"""), "compileSdk = 36")

      appBuildGradle.writeText(content)
    }
    val rootBuildGradle = File(projectRoot, "build.gradle.kts")
    if (rootBuildGradle.exists()) {
      var content = rootBuildGradle.readText()
      content = content.replace(Regex("""alias\(libs\.plugins\.kotlin\.serialization\)\s*(apply\s+false)?"""), "")
      if (isAgp9) {
        content =
          content.replace(
            Regex("""id\("org\.jetbrains\.kotlin\.android"\)\s+version\s+"[^"]+"\s+apply\s+false"""),
            "id(\"org.jetbrains.kotlin.plugin.compose\") version \"$kotlinVersion\" apply false",
          )
      } else {
        content =
          content.replace(
            Regex("""id\("org\.jetbrains\.kotlin\.android"\)\s+version\s+"[^"]+"\s+apply\s+false"""),
            "id(\"org.jetbrains.kotlin.android\") version \"$kotlinVersion\" apply false\n    id(\"org.jetbrains.kotlin.plugin.compose\") version \"$kotlinVersion\" apply false",
          )
      }
      rootBuildGradle.writeText(content)
    }
    val libsVersionsToml = File(projectRoot, "gradle/libs.versions.toml")
    if (libsVersionsToml.exists()) {
      var content = libsVersionsToml.readText()
      content = content.replace(Regex("""\bkotlin\s*=\s*"[^"]+""""), "kotlin = \"$kotlinVersion\"")
      libsVersionsToml.writeText(content)
    }

    patchProjectSourceFiles(projectRoot)
  }

  fun patchProjectSourceFiles(projectRoot: File) {
    val srcDir = File(projectRoot, "app/src/main/java/com/example/mynewproject")
    if (srcDir.exists()) {
      srcDir.listFiles()?.forEach { file ->
        if (file.name != "MainActivity.kt") {
          file.deleteRecursively()
        }
      }
      val mainActivity = File(srcDir, "MainActivity.kt")
      mainActivity.writeText(
        """
        package com.example.mynewproject

        import android.os.Bundle
        import androidx.activity.ComponentActivity
        import androidx.activity.compose.setContent
        import androidx.compose.material3.Text

        class MainActivity : ComponentActivity() {
          override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent {
              Text("Hello")
            }
          }
        }
        """
          .trimIndent()
      )
    }
    File(projectRoot, "app/src/androidTest").deleteRecursively()
  }

  fun isAgp9OrHigher(agpVersion: String): Boolean {
    val major = agpVersion.substringBefore('.').toIntOrNull() ?: return false
    return major >= 9
  }
}
