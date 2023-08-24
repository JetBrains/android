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
import org.jetbrains.kotlin.name.ClassId
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

    assertThat(wrapper.getClassId().asString()).isEqualTo("com/example/Foo")
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isFalse()
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

    assertThat(wrapper.getClassId().asString()).isEqualTo("Foo")
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isFalse()
  }

  @Test
  fun kotlinClassWithAnnotations() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
      package com.example
      import dagger.*

      @Module
      @javax.inject.Inject
      class Foo {}
      """
          .trimIndent()
      ) as KtFile

    val element: KtClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getClassId()).isEqualTo(ClassId.fromString("com/example/Foo"))
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isTrue()
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.INJECT)).isTrue()
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.BINDS)).isFalse()
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

    assertThat(wrapper.getClassId()).isEqualTo(ClassId.fromString("com/example/Foo"))
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

    assertThat(wrapper.getClassId().asString()).isEqualTo("com/example/Foo.Companion")
  }

  @Test
  fun kotlinClassAnnotationOnSelfOrParent() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import dagger.Module
        import javax.inject.Inject

        @Module
        class Foo1 {
          @Module
          companion object /* Foo1Companion */ {
          }
        }

        @Inject
        @Module
        class Foo2 {
          @Module
          companion object /* Foo2Companion */ {
          }
        }

        @Module
        class Foo3 {
          @Inject
          @Module
          companion object /* Foo3Companion */ {
          }
        }
        """
          .trimIndent()
      ) as KtFile

    val foo1 =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(myFixture.findParentElement<KtClass>("Foo|1"))
    val foo1Companion =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.findParentElement<KtObjectDeclaration>("obje|ct /* Foo1Companion"))
    assertThat(foo1.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isFalse()
    assertThat(foo1Companion.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT))
      .isFalse()

    val foo2 =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(myFixture.findParentElement<KtClass>("Foo|2"))
    val foo2Companion =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.findParentElement<KtObjectDeclaration>("obje|ct /* Foo2Companion"))
    assertThat(foo2.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isTrue()
    assertThat(foo2Companion.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT))
      .isTrue()

    val foo3 =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(myFixture.findParentElement<KtClass>("Foo|3"))
    val foo3Companion =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile)
        .of(myFixture.findParentElement<KtObjectDeclaration>("obje|ct /* Foo3Companion"))
    assertThat(foo3.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isFalse()
    assertThat(foo3Companion.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT))
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

    assertThat(wrapper.getClassId().asString()).isEqualTo("com/example/Foo")
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isFalse()
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

    assertThat(wrapper.getClassId().asString()).isEqualTo("Foo")
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isFalse()
  }

  @Test
  fun javaClassWithAnnotations() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
      package com.example;

      import dagger.*;

      @Binds
      @Module()
      @Component(true)
      public class Foo {}
      """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Fo|o")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getClassId().asString()).isEqualTo("com/example/Foo")
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.BINDS)).isTrue()
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.MODULE)).isTrue()
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.COMPONENT)).isTrue()
    assertThat(wrapper.getIsAnnotatedWith(DaggerAnnotation.BINDS_INSTANCE)).isFalse()
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

    assertThat(wrapper.getClassId().asString()).isEqualTo("com/example/Foo")
  }

  @Test
  fun javaAnnotationOnSelfOrParent() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        import dagger.Module;
        import javax.inject.Inject;

        @Module
        public class Foo1 {
          @Module
          public class Inner1 {}
        }

        @Inject
        @Module
        public class Foo2 {
          @Module
          public class Inner2 {}
        }

        @Module
        public class Foo3 {
          @Inject
          @Module
          public class Inner3 {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val foo1 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile).of(myFixture.findParentElement<PsiClass>("Foo|1"))
    val inner1 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.findParentElement<PsiClass>("Inner|1"))
    assertThat(foo1.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isFalse()
    assertThat(inner1.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isFalse()

    val foo2 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile).of(myFixture.findParentElement<PsiClass>("Foo|2"))
    val inner2 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.findParentElement<PsiClass>("Inner|2"))
    assertThat(foo2.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isTrue()
    // Inner class is not a Companion, so this is false.
    assertThat(inner2.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isFalse()

    val foo3 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile).of(myFixture.findParentElement<PsiClass>("Foo|3"))
    val inner3 =
      DaggerIndexPsiWrapper.JavaFactory(psiFile)
        .of(myFixture.findParentElement<PsiClass>("Inner|3"))
    assertThat(foo3.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isFalse()
    assertThat(inner3.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.INJECT)).isTrue()
  }
}
