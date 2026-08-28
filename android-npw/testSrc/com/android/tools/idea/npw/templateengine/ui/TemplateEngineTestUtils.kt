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

import com.android.template.engine.TemplateDefinition
import com.android.template.engine.TemplateDefinitionStorage
import com.android.template.engine.TemplateEngineFactory
import com.android.template.engine.TemplateFile
import com.android.template.engine.TemplateFileEntry
import com.android.template.engine.TemplateFileLoader
import com.android.template.engine.TemplateMessageSink
import com.android.template.engine.TemplateMetadata
import com.android.tools.idea.testing.ResolvedAgpVersionSoftwareEnvironment
import java.io.File

object TemplateEngineTestUtils {
  fun createTestTemplateMetadata(
    name: String = "Test Template",
    shortName: String = "test-template",
    tags: List<String> = emptyList(),
  ): TemplateMetadata {
    val json =
      """
      {
        "name": "$name",
        "short-name": "$shortName",
        "tags": [${tags.joinToString(",") { "\"$it\"" }}]
      }
      """
        .trimIndent()
    return TemplateEngineFactory.createDefault()
      .createTemplateListBuilder(TemplateMessageSink.createDefault(TemplateMessageSink.Severity.Error))
      .parseTemplateMetadata("template-definition.json", json)!!
  }

  fun createTestTemplateDefinition(
    name: String = "Test Template",
    shortName: String = "test-template",
    tags: List<String> = emptyList(),
    files: List<TemplateFileEntry> = emptyList(),
    extraFiles: List<TemplateFileEntry> = emptyList(),
  ): TemplateDefinition {
    val metadata = createTestTemplateMetadata(name, shortName, tags)
    val dummyLoader =
      object : TemplateFileLoader {
        override val storage: TemplateDefinitionStorage =
          object : TemplateDefinitionStorage {
            override fun open(): TemplateDefinitionStorage.Handle =
              object : TemplateDefinitionStorage.Handle {
                override fun close() {}
              }
          }

        override fun <R> withLoader(block: (TemplateFileLoader.Loader) -> R): R =
          block(
            object : TemplateFileLoader.Loader {
              override fun loadFile(entry: TemplateFileEntry): TemplateFile = TemplateFile(entry.relativePath, ByteArray(0))
            }
          )
      }
    return TemplateDefinition(metadata, files, extraFiles, dummyLoader)
  }

  /** Patches generated project build files and sources so that they compile and assemble cleanly in Bazel test environment. */
  fun patchAdditionalVersions(projectRoot: File, resolvedAgp: ResolvedAgpVersionSoftwareEnvironment? = null) {
    val appBuildGradle = File(projectRoot, "app/build.gradle.kts")
    if (appBuildGradle.exists()) {
      var content = appBuildGradle.readText()
      content = content.replace(Regex("""alias\(libs\.plugins\.kotlin\.serialization\)"""), "")
      content = content.replace(Regex("""kotlin\s*\{\s*jvmToolchain\(\s*17\s*\)\s*\}"""), "")
      content = content.replace(Regex("""compileSdk(Version)?\s*(=\s*)?"?(android-)?\d+"?"""), "compileSdk = 36")
      appBuildGradle.writeText(content)
    }

    val rootBuildGradle = File(projectRoot, "build.gradle.kts")
    if (rootBuildGradle.exists()) {
      var content = rootBuildGradle.readText()
      content = content.replace(Regex("""alias\(libs\.plugins\.kotlin\.serialization\)\s*(apply\s+false)?"""), "")
      rootBuildGradle.writeText(content)
    }

    val libsVersionsToml = File(projectRoot, "gradle/libs.versions.toml")
    if (libsVersionsToml.exists() && resolvedAgp != null) {
      var content = libsVersionsToml.readText()
      content = content.replace(Regex("""\bkotlin\s*=\s*"[^"]+""""), "kotlin = \"${resolvedAgp.kotlinVersion}\"")
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
}
