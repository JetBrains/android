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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiManager
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DeclarativeCatalogDependencyReferenceContributorTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  @Before
  fun setUp() {
    Registry.get("android.gradle.declarative.plugin.studio.support").setValue(true)
  }

  @After
  fun tearDown() {
    Registry.get("android.gradle.declarative.plugin.studio.support").resetToDefault()
  }

  @Test
  fun testSimpleLibraryReference() {
    doTest(
      """
      [dependencies]
      implementation = [
        { alias = "libs.gu^ava" }
      ]
    """.trimIndent(),
      "guava = { module = \"com.google.guava:guava\", version.ref = \"guava\" }",
      "libs.versions.toml"
    )
  }

  @Test
  fun testBundleReference() {
    doTest(
      """
      [dependencies]
      implementation = [
        { alias = "libs.bundles.bo^th" }
      ]
    """.trimIndent(),
      "both = [\"constraint-layout\", \"guava\"]",
      "libs.versions.toml"
    )
  }

  @Test
  fun testSecondCatalog() {
    doTest(
      """
      [dependencies]
      implementation = [
        { alias = "libsTest.jun^it" }
      ]
    """.trimIndent(),
      "junit = { module = \"junit:junit\", version.ref = \"junit\" }",
      "libsTest.versions.toml"
    )
  }

  @Test
  fun testComplexName() {
    doTest(
      """
      [dependencies]
      implementation = [
        { alias = "lib^s.constraint.layout" }
      ]
    """.trimIndent(),
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }",
      "libs.versions.toml"
    )
  }

  @Test
  fun testAsKey() {
    doTest(
      """
      dependencies.implementation = [
        { alias = "lib^s.constraint.layout" }
      ]
    """.trimIndent(),
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }",
      "libs.versions.toml"
    )
  }

  @Test
  fun testAsArrayTable() {
    doTest(
      """
      [[dependencies.implementation]]
       alias = "lib^s.constraint.layout"
    """.trimIndent(),
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }",
      "libs.versions.toml"
    )
  }

  private fun doTest(declarativeText: String, elementName: String, filename: String) {
    val caret = declarativeText.indexOf('^')
    Truth.assertWithMessage("The declarativeText must include ^ somewhere to point to reference").that(caret).isNotEqualTo(-1)
    val withoutCaret = declarativeText.substring(0, caret) + declarativeText.substring(caret + 1)

    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)

    val declarativeFile = projectRule.fixture.createFile("build.gradle.toml", withoutCaret)
    projectRule.fixture.openFileInEditor(declarativeFile)

    runReadAction {
      val file = PsiManager.getInstance(projectRule.project).findFile(declarativeFile)!!
      val referee = file.findElementAt(caret)!!.parent
      Truth.assertThat(referee.references.size).isEqualTo(1)
      val referenceTarget = referee.references[0].resolve()
      Truth.assertThat(referenceTarget).isNotNull()
      Truth.assertThat(referenceTarget!!.containingFile.name).isEqualTo(filename)
      Truth.assertThat(referenceTarget.text).isEqualTo(elementName)
    }
  }

}