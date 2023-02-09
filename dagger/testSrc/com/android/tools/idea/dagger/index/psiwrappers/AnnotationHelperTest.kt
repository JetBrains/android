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
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class AnnotationHelperTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun kotlinAnnotation() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import com.other.*

      @Annotation
      class Foo {}
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<KtClass>()!!
    val importHelper = KotlinImportHelper(psiFile)

    assertThat(element.getIsAnnotatedWith("Annotation", importHelper)).isTrue()
    assertThat(element.getIsAnnotatedWith("com.example.Annotation", importHelper)).isTrue()
    assertThat(element.getIsAnnotatedWith("com.other.Annotation", importHelper)).isTrue()
    assertThat(element.getIsAnnotatedWith("com.notimported.Annotation", importHelper)).isFalse()
  }

  @Test
  fun kotlinFullyQualifiedAnnotation() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import com.other.*

      @com.qualified.Annotation
      class Foo {}
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<KtClass>()!!
    val importHelper = KotlinImportHelper(psiFile)

    assertThat(element.getIsAnnotatedWith("Annotation", importHelper)).isFalse()
    assertThat(element.getIsAnnotatedWith("com.example.Annotation", importHelper)).isFalse()
    assertThat(element.getIsAnnotatedWith("com.other.Annotation", importHelper)).isFalse()
    assertThat(element.getIsAnnotatedWith("com.qualified.Annotation", importHelper)).isTrue()
  }

  @Test
  fun kotlinAnnotationWithAlias() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import com.aliased.Annotation as Bar

      @Bar
      class Foo {}
      """.trimIndent()
      ) as
        KtFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<KtClass>()!!
    val importHelper = KotlinImportHelper(psiFile)

    assertThat(element.getIsAnnotatedWith("com.aliased.Annotation", importHelper)).isTrue()
  }

  @Test
  fun javaAnnotation() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      import com.other.*;

      @Annotation
      class Foo {}
      """.trimIndent()
      ) as
        PsiJavaFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<PsiClass>()!!
    val importHelper = JavaImportHelper(psiFile)

    assertThat(element.getIsAnnotatedWith("Annotation", importHelper)).isTrue()
    assertThat(element.getIsAnnotatedWith("com.example.Annotation", importHelper)).isTrue()
    assertThat(element.getIsAnnotatedWith("com.other.Annotation", importHelper)).isTrue()
    assertThat(element.getIsAnnotatedWith("com.notimported.Annotation", importHelper)).isFalse()
  }

  @Test
  fun javaFullyQualifiedAnnotation() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      import com.other.*;

      @com.qualified.Annotation
      class Foo {}
      """.trimIndent()
      ) as
        PsiJavaFile

    val element = myFixture.moveCaret("Fo|o").parentOfType<PsiClass>()!!
    val importHelper = JavaImportHelper(psiFile)

    assertThat(element.getIsAnnotatedWith("Annotation", importHelper)).isFalse()
    assertThat(element.getIsAnnotatedWith("com.example.Annotation", importHelper)).isFalse()
    assertThat(element.getIsAnnotatedWith("com.other.Annotation", importHelper)).isFalse()
    assertThat(element.getIsAnnotatedWith("com.qualified.Annotation", importHelper)).isTrue()
  }
}
