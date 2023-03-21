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
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
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
class DaggerIndexAnnotatedWrapperTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  private fun DaggerIndexAnnotatedWrapper.getAnnotationByNameTestHelper(
    fqName: String
  ): List<String> = getAnnotationsByName(fqName).toList().map { it.getAnnotationNameInSource() }

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
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.other.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.notimported.Annotation")).isFalse()

    assertThat(wrapper.getAnnotationByNameTestHelper("Annotation")).containsExactly("Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.example.Annotation"))
      .containsExactly("Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.other.Annotation"))
      .containsExactly("Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.notimported.Annotation")).isEmpty()
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
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("Annotation")).isFalse()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
    assertThat(wrapper.getIsAnnotatedWith("com.other.Annotation")).isFalse()
    assertThat(wrapper.getIsAnnotatedWith("com.qualified.Annotation")).isTrue()

    assertThat(wrapper.getAnnotationByNameTestHelper("Annotation")).isEmpty()
    assertThat(wrapper.getAnnotationByNameTestHelper("com.example.Annotation")).isEmpty()
    assertThat(wrapper.getAnnotationByNameTestHelper("com.other.Annotation")).isEmpty()
    assertThat(wrapper.getAnnotationByNameTestHelper("com.qualified.Annotation"))
      .containsExactly("com.qualified.Annotation")
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
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("com.aliased.Annotation")).isTrue()
    assertThat(wrapper.getAnnotationByNameTestHelper("com.aliased.Annotation"))
      .containsExactly("Bar")
  }

  @Test
  fun kotlinMultipleAnnotations() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      import com.other.*

      @Annotation
      @Annotation
      @com.other.Annotation
      @com.qualified1.Annotation
      @com.qualified2.Annotation
      class Foo {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.other.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.qualified1.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.qualified2.Annotation")).isTrue()

    assertThat(wrapper.getAnnotationByNameTestHelper("Annotation"))
      .containsExactly("Annotation", "Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.example.Annotation"))
      .containsExactly("Annotation", "Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.other.Annotation"))
      .containsExactly("Annotation", "Annotation", "com.other.Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.qualified1.Annotation"))
      .containsExactly("com.qualified1.Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.qualified2.Annotation"))
      .containsExactly("com.qualified2.Annotation")
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
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.other.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.notimported.Annotation")).isFalse()

    assertThat(wrapper.getAnnotationByNameTestHelper("Annotation")).containsExactly("Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.example.Annotation"))
      .containsExactly("Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.other.Annotation"))
      .containsExactly("Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.notimported.Annotation")).isEmpty()
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
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("Annotation")).isFalse()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
    assertThat(wrapper.getIsAnnotatedWith("com.other.Annotation")).isFalse()
    assertThat(wrapper.getIsAnnotatedWith("com.qualified.Annotation")).isTrue()

    assertThat(wrapper.getAnnotationByNameTestHelper("Annotation")).isEmpty()
    assertThat(wrapper.getAnnotationByNameTestHelper("com.example.Annotation")).isEmpty()
    assertThat(wrapper.getAnnotationByNameTestHelper("com.other.Annotation")).isEmpty()
    assertThat(wrapper.getAnnotationByNameTestHelper("com.qualified.Annotation"))
      .containsExactly("com.qualified.Annotation")
  }

  @Test
  fun javaMultipleAnnotations() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      import com.other.*;

      @Annotation
      @Annotation
      @com.other.Annotation
      @com.qualified1.Annotation
      @com.qualified2.Annotation
      class Foo {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getIsAnnotatedWith("Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.other.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.qualified1.Annotation")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.qualified2.Annotation")).isTrue()

    assertThat(wrapper.getAnnotationByNameTestHelper("Annotation"))
      .containsExactly("Annotation", "Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.example.Annotation"))
      .containsExactly("Annotation", "Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.other.Annotation"))
      .containsExactly("Annotation", "Annotation", "com.other.Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.qualified1.Annotation"))
      .containsExactly("com.qualified1.Annotation")
    assertThat(wrapper.getAnnotationByNameTestHelper("com.qualified2.Annotation"))
      .containsExactly("com.qualified2.Annotation")
  }
}
