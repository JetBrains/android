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
package com.android.tools.idea.gradle.refactoring

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiManager

/** Tests for [VersionCatalogGoToDeclarationHandler]. */
class VersionCatalogGoToDeclarationHandlerTest : AndroidGradleTestCase() {
  fun testGoToDeclarationInToml() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS)

    // Check Go To Declaration within the TOML file

    // Navigate from version.ref string to corresponding version variable
    checkUsage(
      "gradle/libs.versions.toml",
      "version.ref = \"gua|va\"",
      "guava = \"19.0\""
    )

    // Same, but for a variable with dashes
    checkUsage(
      "gradle/libs.versions.toml",
      "version.ref = \"cons|traint-layout\"",
      "constraint-layout = \"1.0.2\""
    )

    // Navigate from library name in a bundle array to corresponding library
    checkUsage(
      "gradle/libs.versions.toml",
      "both = [\"constraint-layout\", \"|guava\"]",
      "guava = { module = \"com.google.guava:guava\", version.ref = \"guava\" }"
    )

    // Same, but for a library variable with dashes
    checkUsage(
      "gradle/libs.versions.toml",
      "both = [\"const|raint-layout\", \"guava\"]",
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }"
    )

    // Same, but for a library variable with dashes
    checkUsage(
      "gradle/libs.versions.toml",
      "both = [\"const|raint-layout\", \"guava\"]",
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }"
    )
  }

  fun testGotoCatalogDeclarationInKts(){
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS)
    // Navigate from KTS catalog reference to TOML library
    checkUsage(
      "app/build.gradle.kts",
      "api(libs.|guava)",
      "guava = { module = \"com.google.guava:guava\", version.ref = \"guava\" }"
    )

    // Navigate from KTS catalog reference to TOML library, with a dotted name (mapped to dashed name in TOML)
    checkUsage(
      "app/build.gradle.kts",
      "libs.constraint.la|yout",
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }"
    )

    // Same, but clicking on an earlier part in the reference; there isn't an exact TOML library for the group, so we
    // need to pick the first one
    checkUsage(
      "app/build.gradle.kts",
      "libs.cons|traint.layout",
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }"
    )

    // Navigate to the [plugins] block in TOML
    checkUsage(
      "app/build.gradle.kts",
      "alias(libs.plu|gins.kotlinAndroid)",
      "[plugins]"
    )

    // Navigate from a KTS plugin reference to the plugin in the TOML file
    checkUsage(
      "app/build.gradle.kts",
      "alias(libs.plugins.android.appli|cation)",
      "android-application = { id = \"com.android.application\", version.ref = \"agpVersion\" }"
    )

    // Navigate to the [bundles] block in TOML
    checkUsage(
      "app/build.gradle.kts",
      "api(libs.b|undles.both)",
      "[bundles]"
    )

    // Navigate to the [bundles] block in TOML
    checkUsage(
      "app/build.gradle.kts",
      "api(libs.bundles.|both)",
      "both = [\"constraint-layout\", \"guava\"]"
    )

    // Navigate from a KTS to second catalog
    checkUsage(
      "app/build.gradle.kts",
      "testImplementation(libsTest.j|unit)",
      "junit = { module = \"junit:junit\", version.ref = \"junit\" }"
    )
  }

  fun testGotoCatalogDeclarationInGroovy(){
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    // Navigate from groovy catalog reference to TOML library
    checkUsage(
      "app/build.gradle",
      "api libs.|guava",
      "guava = { module = \"com.google.guava:guava\", version.ref = \"guava\" }"
    )

    // Navigate from groovy catalog reference to TOML library, with a dotted name (mapped to dashed name in TOML)
    checkUsage(
      "app/build.gradle",
      "libs.constraint.la|yout",
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }"
    )

    // Same, but clicking on an earlier part in the reference; there isn't an exact TOML library for the group, so we
    // need to pick the first one
    checkUsage(
      "app/build.gradle",
      "libs.cons|traint.layout",
      "constraint-layout = { module = \"com.android.support.constraint:constraint-layout\", version.ref = \"constraint-layout\" }"
    )

    // Navigate to the [plugins] block in TOML
   checkUsage(
      "app/build.gradle",
      "alias libs.plug|ins.android.application",
      "[plugins]"
    )

    // Navigate from a ksp plugin reference to the plugin in the TOML file
    checkUsage(
      "app/build.gradle",
      "alias libs.plugins.andr|oid.application",
      "android-application = { id = \"com.android.application\", version.ref = \"gradlePlugins-agp\" }"
    )

    // Navigate to the [bundles] block in TOML
    checkUsage(
      "app/build.gradle",
      "api libs.b|undles.both",
      "[bundles]"
    )

    // Navigate to the [bundles] block in TOML
    checkUsage(
      "app/build.gradle",
      "api libs.bundles.|both",
      "both = [\"constraint-layout\", \"guava\"]"
    )

    // Navigate from a groovy to second catalog
    checkUsage(
      "app/build.gradle",
      "testImplementation libsTest.j|unit",
      "junit = { module = \"junit:junit\", version.ref = \"junit\" }"
    )
  }

  private fun checkUsage(relativePath: String, caretContext: String, expected: String) {
    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val source = root.findFileByRelativePath(relativePath)!!
    val psiFile = PsiManager.getInstance(project).findFile(source)!!

    // Figure out the caret offset in the file given a substring from the source with "|" somewhere in that
    // substring indicating the exact spot of the caret
    val text = psiFile.text
    val caretDelta = caretContext.indexOf('|')
    assertWithMessage("The caretContext must include | somewhere to point to the caret position").that(caretDelta).isNotEqualTo(-1)
    val withoutCaret = caretContext.substring(0, caretDelta) + caretContext.substring(caretDelta + 1)
    val index = text.indexOf(withoutCaret)
    assertWithMessage("Did not find `$withoutCaret` in $relativePath").that(index).isNotEqualTo(-1)
    val caret = index + caretDelta

    val handler = VersionCatalogGoToDeclarationHandler()
    val element = psiFile.findElementAt(caret)
    val target = handler.getGotoDeclarationTarget(element, null)
    assertWithMessage("Didn't find a go to destination from $caretContext").that(target).isNotNull()
    assertThat(target?.text?.substringBefore("\n")).isEqualTo(expected)
  }
}
