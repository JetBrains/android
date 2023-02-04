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
package com.android.tools.idea.dagger.index.psiwrappers

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class ImportHelperTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun kotlin_getPossibleAnnotationText_noImports() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      """.trimIndent()) as KtFile

    val importHelper = KotlinImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }

  @Test
  fun kotlin_getPossibleAnnotationText_starImports() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.*
      """.trimIndent()) as KtFile

    val importHelper = KotlinImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject", "Inject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }

  @Test
  fun kotlin_getPossibleAnnotationText_directImport() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.Inject
      """.trimIndent()) as KtFile

    val importHelper = KotlinImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject", "Inject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }

  @Test
  fun kotlin_getPossibleAnnotationText_importWithAlias() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.Inject as OtherInject
      """.trimIndent()) as KtFile

    val importHelper = KotlinImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject", "OtherInject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }

  @Test
  fun `kotlin_getPossibleAnnotationText_allTheImports!!!`() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      import javax.inject.*
      import javax.inject.Inject
      import javax.inject.Inject as OtherInject
      """.trimIndent()) as KtFile

    val importHelper = KotlinImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject", "Inject",
                                                                                              "OtherInject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }

  @Test
  fun kotlin_aliasMap_noImports() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example
      """.trimIndent()) as KtFile

    val importHelper = KotlinImportHelper(psiFile)

    assertThat(importHelper.aliasMap).isEmpty()
  }

  @Test
  fun kotlin_aliasMap_noAliases() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      import javax.import.*
      import com.other.Foo
      import java.util.List
      """.trimIndent()) as KtFile

    val importHelper = KotlinImportHelper(psiFile)

    assertThat(importHelper.aliasMap).isEmpty()
  }

  @Test
  fun kotlin_aliasMap_aliases() {
    val psiFile = myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package com.example

      import javax.import.*
      import com.other.Foo as Bar
      import java.util.List as MyList
      """.trimIndent()) as KtFile

    val importHelper = KotlinImportHelper(psiFile)

    assertThat(importHelper.aliasMap).containsExactly("Bar", "Foo", "MyList", "List")
  }

  @Test
  fun java_getPossibleAnnotationText_noImports() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      """.trimIndent()) as PsiJavaFile

    val importHelper = JavaImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }

  @Test
  fun java_getPossibleAnnotationText_starImports() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import javax.inject.*;
      """.trimIndent()) as PsiJavaFile

    val importHelper = JavaImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject", "Inject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }

  @Test
  fun java_getPossibleAnnotationText_directImport() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import javax.inject.Inject;
      """.trimIndent()) as PsiJavaFile

    val importHelper = JavaImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject", "Inject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }

  @Test
  fun java_getPossibleAnnotationText_importedBothWays() {
    val psiFile = myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=java
      """
      package com.example;
      import javax.inject.*
      import javax.inject.Inject;
      """.trimIndent()) as PsiJavaFile

    val importHelper = JavaImportHelper(psiFile)

    assertThat(importHelper.getPossibleAnnotationText("javax.inject.Inject")).containsExactly("javax.inject.Inject", "Inject")
    assertThat(importHelper.getPossibleAnnotationText("com.example.Inject")).containsExactly("com.example.Inject", "Inject")
  }
}
