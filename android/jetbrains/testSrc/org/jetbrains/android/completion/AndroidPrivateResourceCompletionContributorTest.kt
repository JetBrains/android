/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.completion

import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class AndroidPrivateResourceCompletionContributorTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk().onEdt()
  private val project by lazy { projectRule.project }
  private val fixture by lazy { projectRule.fixture }
  private val module by lazy { projectRule.projectRule.module }

  @Before
  fun setUp() {
    // We must add a manifest otherwise we'll have no package name to check.
    fixture.addFileToProject(
      "AndroidManifest.xml",
      """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
      <application />
      </manifest>
      """
        .trimIndent(),
    )
    addAarDependency(fixture, module, "aarLib", "com.example.aarLib") { resDir ->
      resDir.parentFile
        .resolve(FN_RESOURCE_TEXT)
        .writeText(
          """
          int color publicColor 0x7f010001
          int color privateColor 0x7f010002
          """
            .trimIndent()
        )
      resDir.parentFile
        .resolve(FN_PUBLIC_TXT)
        .writeText(
          """
          color publicColor
          """
            .trimIndent()
        )
      resDir
        .resolve("values/colors.xml")
        .writeText(
          // language=XML
          """
          <resources>
            <color name="publicColor">#008577</color>
            <color name="privateColor">#DEADBE</color>
          </resources>
          """
            .trimIndent()
        )
    }
  }

  @Test
  fun privateResourcesFiltered_java() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.java",
          """
          package com.example;
          class Foo {
            public void bar() {
              int color = R.color.col
            }
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString)).containsExactly("publicColor")
  }

  @Test
  fun privateResourcesFiltered_withPackage_java() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.java",
          """
          package com.example;
          class Foo {
            public void bar() {
              int color = com.example.R.color.col
            }
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString)).containsExactly("publicColor")
  }

  @Test
  fun privateResourcesNotFiltered_java() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.java",
          """
          package com.example;
          class Foo {
            public void bar() {
              int color = com.example.aarLib.R.color.col
            }
          }
          """
            .trimIndent(),
        )
        .virtualFile
    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString))
      .containsExactly("publicColor", "privateColor")
  }

  @Test
  fun privateResourcesFiltered_kotlin() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          """
          package com.example
          fun bar() {
            val color = R.color.col
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString)).containsExactly("publicColor")
  }

  @Test
  fun privateResourcesFiltered_withPackage_kotlin() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          """
          package com.example
          fun bar() {
            val color = com.example.R.color.col
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString)).containsExactly("publicColor")
  }

  @Test
  fun privateResourcesNotFiltered_kotlin() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          """
          package com.example
          fun bar() {
            val color = com.example.aarLib.R.color.col
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString))
      .containsExactly("publicColor", "privateColor")
  }
}
