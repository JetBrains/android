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
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexClassWrapperTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun kotlinClassWithPackage() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      class Foo {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun kotlinClassWithoutPackage() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      class Foo {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun kotlinClassWithAnnotations() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      @Annotation1
      @Annotation2()
      @Annotation3(true)
      class Foo {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation1")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation2")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation3")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation4")).isFalse()
  }

  @Test
  fun kotlinClassWithGeneric() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example

      class Foo<A, B> {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
  }

  @Test
  fun kotlinClassCompanionFqName() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        class Foo {
          companion object {}
        }
        """
          .trimIndent()
      ) as KtFile

    val element: KtObjectDeclaration = myFixture.findParentElement("companion obj|ect")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo.Companion")
  }

  @Test
  fun kotlinClassAnnotationOnSelfOrParent() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        @AnnotationOnAll
        class Foo1 {
          @AnnotationOnAll
          companion object /* Foo1Companion */ {
          }
        }

        @Annotation1
        @AnnotationOnAll
        class Foo2 {
          @AnnotationOnAll
          companion object /* Foo2Companion */ {
          }
        }

        @AnnotationOnAll
        class Foo3 {
          @Annotation1
          @AnnotationOnAll
          companion object /* Foo3Companion */ {
          }
        }
        """
          .trimIndent()
      ) as KtFile

    val foo1 =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.moveCaret("Foo|1").parentOfType<KtClass>()!!)
    val foo1Companion =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.moveCaret("obje|ct /* Foo1Companion").parentOfType<KtObjectDeclaration>()!!)
    assertThat(foo1.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isFalse()
    assertThat(foo1Companion.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1"))
      .isFalse()

    val foo2 =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.moveCaret("Foo|2").parentOfType<KtClass>()!!)
    val foo2Companion =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.moveCaret("obje|ct /* Foo2Companion").parentOfType<KtObjectDeclaration>()!!)
    assertThat(foo2.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isTrue()
    assertThat(foo2Companion.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1"))
      .isTrue()

    val foo3 =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.moveCaret("Foo|3").parentOfType<KtClass>()!!)
    val foo3Companion =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.moveCaret("obje|ct /* Foo3Companion").parentOfType<KtObjectDeclaration>()!!)
    assertThat(foo3.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isFalse()
    assertThat(foo3Companion.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1"))
      .isTrue()
  }

  @Test
  fun javaClassWithPackage() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;
      public class Foo {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun javaClassWithoutPackage() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      public class Foo {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation")).isFalse()
  }

  @Test
  fun javaClassWithAnnotations() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      @Annotation1
      @Annotation2()
      @Annotation3(true)
      public class Foo {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation1")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation2")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation3")).isTrue()
    assertThat(wrapper.getIsAnnotatedWith("com.example.Annotation4")).isFalse()
  }

  @Test
  fun javaClassWithGeneric() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      public class Foo<A, B> {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getFqName()).isEqualTo("com.example.Foo")
  }

  @Test
  fun javaAnnotationOnSelfOrParent() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        @AnnotationOnAll
        public class Foo1 {
          @AnnotationOnAll
          public class Inner1 {}
        }

        @Annotation1
        @AnnotationOnAll
        public class Foo2 {
          @AnnotationOnAll
          public class Inner2 {}
        }

        @AnnotationOnAll
        public class Foo3 {
          @Annotation1
          @AnnotationOnAll
          public class Inner3 {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val foo1 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.moveCaret("Foo|1").parentOfType<PsiClass>()!!)
    val inner1 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.moveCaret("Inner|1").parentOfType<PsiClass>()!!)
    assertThat(foo1.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isFalse()
    assertThat(inner1.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isFalse()

    val foo2 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.moveCaret("Foo|2").parentOfType<PsiClass>()!!)
    val inner2 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.moveCaret("Inner|2").parentOfType<PsiClass>()!!)
    assertThat(foo2.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isTrue()
    // Inner class is not a Companion, so this is false.
    assertThat(inner2.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isFalse()

    val foo3 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.moveCaret("Foo|3").parentOfType<PsiClass>()!!)
    val inner3 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.moveCaret("Inner|3").parentOfType<PsiClass>()!!)
    assertThat(foo3.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isFalse()
    assertThat(inner3.getIsSelfOrCompanionParentAnnotatedWith("com.example.Annotation1")).isTrue()
  }
}
