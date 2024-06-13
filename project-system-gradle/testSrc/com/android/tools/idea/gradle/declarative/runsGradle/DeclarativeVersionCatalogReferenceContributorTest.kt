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
package com.android.tools.idea.gradle.declarative.runsGradle

import com.android.SdkConstants.FN_BUILD_GRADLE_DECLARATIVE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.declarative.psi.DeclarativeProperty
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiManager
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.findTopmostParentOfType
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.toml.lang.psi.TomlKeyValue

@RunsInEdt
class DeclarativeVersionCatalogReferenceContributorTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  @Before
  fun setUp() {
    Registry.get("android.gradle.declarative.plugin.studio.support").setValue(true)
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true)
  }

  @After
  fun tearDown() {
    Registry.get("android.gradle.declarative.plugin.studio.support").resetToDefault()
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride()
  }

  @Test
  fun testSimpleLibraryReference() {
    doTest(
      """
      dependencies {
        implementation(libs.gu^ava)
      }
    """.trimIndent(),
      "guava = { module = \"com.google.guava:guava\", version.ref = \"guava\" }",
      "libs.versions.toml"
    )
  }

  @Test
  fun testBundleReference() {
    doTest(
      """
      dependencies {
        implementation(libs.bundles.bo^th)
      }
    """.trimIndent(),
      "both = [\"constraint-layout\", \"guava\"]",
      "libs.versions.toml"
    )
  }

  @Test
  fun testSecondCatalog() {
    doTest(
      """
      dependencies {
        implementation(libsTest.jun^it)
      }
    """.trimIndent(),
      "junit = { module = \"junit:junit\", version.ref = \"junit\" }",
      "libsTest.versions.toml"
    )
  }

  @Test
  fun testComplexName() {
    doTest(
      """
      dependencies {
        implementation(libs.constra^int.layout)
      }
    """.trimIndent(),
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }",
      "libs.versions.toml"
    )
  }

  private fun doTest(text: String, elementName: String, filename: String) {
    val caret = text.indexOf('^')
    Truth.assertWithMessage("The text must include ^ somewhere to point to reference").that(caret).isNotEqualTo(-1)
    val withoutCaret = text.substring(0, caret) + text.substring(caret + 1)

    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)

    val file = projectRule.fixture.createFile(FN_BUILD_GRADLE_DECLARATIVE, withoutCaret)
    projectRule.fixture.openFileInEditor(file)

    runReadAction {
      val file = PsiManager.getInstance(projectRule.project).findFile(file)!!
      val referee = file.findElementAt(caret)!!.findTopmostParentOfType<DeclarativeProperty>()!!
      val reference = referee.references.filter { it.absoluteRange.contains(caret) }.singleOrNull()
      Truth.assertThat(reference).isNotNull()
      val referenceTarget = reference!!.resolve()
      Truth.assertThat(referenceTarget).isNotNull()
      Truth.assertThat(referenceTarget!!.containingFile.name).isEqualTo(filename)
      Truth.assertThat(referenceTarget.findParentOfType<TomlKeyValue>()!!.text).isEqualTo(elementName)
    }
  }

}