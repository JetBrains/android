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
package com.android.tools.idea.gradle.catalog.runsGradle

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.testFramework.utils.vfs.createFile
import org.jetbrains.plugins.gradle.codeInspection.toml.UnusedVersionCatalogEntryInspection
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import kotlin.io.path.absolutePathString

@RunsInEdt
class CatalogUnusedHighlightingTest {
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  val project
    get() = projectRule.project

  @Test
  // b/297954569
  fun testNoUsageOfBundle() {
    runTest("", """
        [bundles]
        <warning>ui</warning> = [ "ui1", "ui2" ]
    """.trimIndent())
  }

  @Test
  // b/297954569
  fun testBundleUsage() {
    runTest("libs.bundles.ui", """
        [bundles]
        ui = [ "ui1", "ui2" ]
    """.trimIndent())
  }

  @Test
  fun testLibsUsage() {
    runTest("libs.ui", """
        [libraries]
        ui = "shortNotation"
    """.trimIndent())
  }

  @Test
  // b/295282939
  fun testLibsUsageNestedCase() {
    runTest("""
      implementation(libs.androidx.compose.ui)
      debugImplementation(libs.androidx.compose.ui.test.manifest)
      androidTestImplementation(libs.androidx.compose.ui.test.junit4)
      implementation(libs.androidx.compose.ui.tooling)
      implementation(libs.androidx.compose.ui.tooling.preview)
      implementation(libs.androidx.compose.ui.util)

    """.trimIndent(), """
        [libraries]
        androidx-compose-ui = { module = "androidx.compose.ui:ui" }
        androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
        androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
        androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
        androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
        androidx-compose-ui-util = { module = "androidx.compose.ui:ui-util" }
        <warning>androidx-compose-bom</warning> = { module = "androidx.compose:compose-bom", version.ref = "composeBom"}
    """.trimIndent())
  }

  private fun runTest(buildGradleText:String, catalogContent:String) {
    fixture.enableInspections(UnusedVersionCatalogEntryInspection::class.java)
    val rootFolder = File(project.basePath!!)
    File(rootFolder, "settings.gradle").writeText("rootProject.name = \"Test\"")

    fixture.testDataPath = rootFolder.toPath().parent.absolutePathString()
    projectRule.load(rootFolder.name)

    writeTextAndCommit("build.gradle", buildGradleText)

    val file = writeTextAndCommit("gradle/libs.versions.toml", catalogContent)
    runInEdtAndWait {
      fixture.testHighlighting(true, false, true, file)
    }
  }

  private fun writeTextAndCommit(relativePath: String, text: String): VirtualFile {
    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    return runWriteActionAndWait {
      val file = root.findFileByRelativePath(relativePath) ?: root.createFile(relativePath)
      file.writeTextAndCommit(text)
      file
    }
  }

  private fun VirtualFile.writeTextAndCommit(text: String) {
    findDocument()?.reloadFromDisk()
    writeText(text)
    findDocument()?.commitToPsi(project)
  }

}