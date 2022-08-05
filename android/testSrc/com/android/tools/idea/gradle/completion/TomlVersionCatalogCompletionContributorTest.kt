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
package com.android.tools.idea.gradle.completion

import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestCase
import org.junit.Test

@RunsInEdt
class TomlVersionCatalogCompletionContributorTest : AndroidTestCase() {

  private fun testCompletion(toml: String, list: List<String>){
    val tomlFile = myFixture.addFileToProject("gradle/libs.versions.toml", toml)
    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()
    Truth.assertThat(myFixture.lookupElementStrings).isEqualTo(list)
  }

  @Test
  fun testCompletionInLibraries() {
    testCompletion(
      """
        [libraries]
        compat = { $caret }
      """.trimIndent(),
      listOf("group", "module", "name", "version"))
  }

  @Test
  fun testCompletionInLibraries2() {
    testCompletion(
      """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", $caret }

      """.trimIndent(),
      listOf("version"))
  }

  @Test
  fun testCompletionInPlugins() {
    testCompletion(
      """
        [plugins]
        plugin1 = {$caret}
      """.trimIndent(),
      listOf("id", "version"))
  }

  @Test
  fun testCompletionInVersions() {
    testCompletion(
      """
        [versions]
        agp = {$caret}

      """.trimIndent(),
      listOf("prefer", "reject", "rejectAll", "require", "strictly"))
  }

  @Test
  fun testCompletionInLibraryVersions() {
    testCompletion(
      """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version = {$caret}
      """.trimIndent(),
      listOf("prefer", "ref", "reject", "rejectAll", "require", "strictly"))
  }


  @Test
  fun testCompletionInLibraryVersions2() {
    testCompletion(
      """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version = { prefer ="1.2" $caret}
      """.trimIndent(),
      listOf("reject", "rejectAll", "require", "strictly"))
  }

  @Test
  fun testCompletionInLibraryVersionsWithRequire() {
    testCompletion(
      """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version = { require = "1.2" $caret}
      """.trimIndent(),
      listOf("prefer", "strictly"))
  }

  @Test
  fun testCompletionVersionWithDot() {
    testCompletion(
      """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.$caret }
      """.trimIndent(),
      listOf("prefer", "ref", "reject", "rejectAll", "require", "strictly"))
  }

  @Test
  fun testCompletionVersionWithDotAndOpenCurlyBraces() {
    testCompletion(
      """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.$caret
      """.trimIndent(),
      listOf("prefer", "ref", "reject", "rejectAll", "require", "strictly"))
  }

  @Test
  fun testCompletionVersionWithDotComplexCase() {
    testCompletion(
      """
        [plugins]
        compat = { module = "androidx.appcompat:appcompat", version = { prefer = "3.9" }, version.strictly = "[3.8, 4.0[", version.$caret }
      """.trimIndent(),
      listOf("reject", "rejectAll", "require"))
  }

  @Test
  fun testCompletionVersionWithDotAndSecondLevelDefined() {
    testCompletion(
      """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.ref.$caret }
      """.trimIndent(),
      listOf())
  }

  @Test
  fun testNoCompletionInFoo() {
    testCompletion(
      """
        [foo]
        compat = { module = "androidx.appcompat:appcompat", $caret }
      """.trimIndent(), listOf())
  }

  @Test
  fun testSectionName() {
    testCompletion(
      """
        [$caret]
      """.trimIndent(), listOf("bundles", "libraries", "plugins", "versions"))
  }

  @Test
  fun testSectionNameWithFirstLetter() {
    testCompletion(
      """
        [u$caret]
      """.trimIndent(), listOf("bundles", "plugins"))
  }

  @Test
  fun testSectionNameWithSomeExistingSections() {
    testCompletion(
      """
        [plugins]
        jmh = { id = "me.champeau.jmh", version = "0.6.5" }

        [$caret]
      """.trimIndent(), listOf("bundles", "libraries", "versions"))
  }

  @Test
  fun testCompletionForNonLibrariesTomlFile() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/custom.toml",
      """
        [libraries]
        compat = { $caret }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()
    Truth.assertThat(myFixture.lookupElementStrings).isEqualTo(listOf<String>())

  }
}