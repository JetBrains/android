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
class TomlVersionRefCompletionContributorTest : AndroidTestCase() {
  @Test
  fun testVersionRefCompletionInLibraries() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [versions]
        agp = "7.1.2"
        appcompat = "1.4.0"
        compose = "0.1.2"

        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.ref = "a$caret" }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()
    Truth.assertThat(myFixture.lookupElementStrings).isEqualTo(listOf("agp", "appcompat"))
  }

  @Test
  fun testVersionRefCompletionInPlugins() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [versions]
        agp = "7.1.2"
        appcompat = "1.4.0"
        compose = "0.1.2"

        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }

        [plugins]
        application = { module = "com.android.tools.build:gradle", version.ref = "a$caret" }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()
    Truth.assertThat(myFixture.lookupElementStrings).isEqualTo(listOf("agp", "appcompat"))
  }

  @Test
  fun testRefCompletionInLibraries() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [versions]
        agp = "7.1.2"
        appcompat = "1.4.0"
        compose = "0.1.2"

        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version = { ref = "a$caret" } }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()
    Truth.assertThat(myFixture.lookupElementStrings).isEqualTo(listOf("agp", "appcompat"))
  }

  @Test
  fun testRefCompletionInPlugins() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [versions]
        agp = "7.1.2"
        appcompat = "1.4.0"
        compose = "0.1.2"

        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }

        [plugins]
        application = { module = "com.android.tools.build:gradle", version = { ref = "a$caret" } }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()
    Truth.assertThat(myFixture.lookupElementStrings).isEqualTo(listOf("agp", "appcompat"))
  }

  @Test
  fun testNoRefCompletionWithoutVersion() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [versions]
        agp = "7.1.2"
        appcompat = "1.4.0"
        compose = "0.1.2"

        [libraries]
        compat = { module = "androidx.appcompat:appcompat", foo = { ref = "a$caret" } }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()
    Truth.assertThat(myFixture.lookupElementStrings).isEmpty()
  }

  @Test
  fun testNoCompletionInFoo() {
    val tomlFile = myFixture.addFileToProject(
      "gradle/libs.versions.toml",
      """
        [versions]
        agp = "7.1.2"
        appcompat = "1.4.0"
        compose = "0.1.2"

        [foo]
        compat = { module = "androidx.appcompat:appcompat", version.ref = "a$caret" }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)
    myFixture.completeBasic()
    Truth.assertThat(myFixture.lookupElementStrings).isEmpty()
  }
}