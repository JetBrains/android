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
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypeElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtTypeReference
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerIndexTypeWrapperTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun kotlinUnqualifiedType() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example
        class Foo {
          fun bar(): Baz {}
        }
        """
          .trimIndent()
      ) as KtFile

    val element: KtTypeReference = myFixture.findParentElement("Ba|z {}")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun kotlinUnqualifiedTypeWithImport() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example
        import com.other.Baz

        class Foo {
          fun bar(): Baz {}
        }
        """
          .trimIndent()
      ) as KtFile

    val element: KtTypeReference = myFixture.findParentElement("Ba|z {}")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun kotlinUnqualifiedTypeWithImportAlias() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example
        import com.other.Aliased as Baz

        class Foo {
          fun bar(): Baz {}
        }
        """
          .trimIndent()
      ) as KtFile

    val element: KtTypeReference = myFixture.findParentElement("Ba|z {}")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Aliased")
  }

  @Test
  fun kotlinQualifiedType() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        class Foo {
          fun bar(): com.other.Baz {}
        }
        """
          .trimIndent()
      ) as KtFile

    val element: KtTypeReference = myFixture.findParentElement("Ba|z {}")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun kotlinInnerClassType() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example
        import com.other.Baz

        class Foo {
          fun bar(): Baz.InnerClass {}
        }
        """
          .trimIndent()
      ) as KtFile

    val element: KtTypeReference = myFixture.findParentElement("Ba|z.InnerClass {}")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("InnerClass")
  }

  @Test
  fun kotlinAliasedInnerClassType() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example
        import com.other.Aliased as Baz

        class Foo {
          fun bar(): Baz.InnerClass {}
        }
        """
          .trimIndent()
      ) as KtFile

    val element: KtTypeReference = myFixture.findParentElement("Ba|z.InnerClass {}")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("InnerClass")
  }

  @Test
  fun kotlinStarImportType() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example
        import com.other.*

        class Foo {
          fun bar(): Baz {}
        }
        """
          .trimIndent()
      ) as KtFile

    val element: KtTypeReference = myFixture.findParentElement("Ba|z {}")
    val wrapper = DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun kotlinGenericTypes() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example
        import java.util.Optional
        import java.util.Optional as OptionalAlias

        class Foo {
          fun bar1(): List<Optional<String>> {}
          fun bar2(): kotlin.collections.List<java.util.Optional<kotlin.String>> {}
          fun bar3(): kotlin.collections.List<OptionalAlias<kotlin.String>> {}
          fun bar4(): kotlin.Map<String, List<String>> {}
        }
        """
          .trimIndent()
      ) as KtFile

    val bar1ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|1()").getReturnTypeReference()!!
    val bar1ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar1ReturnTypeElement)

    assertThat(bar1ReturnTypeWrapper.getSimpleName()).isEqualTo("List")

    val bar2ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|2()").getReturnTypeReference()!!
    val bar2ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar2ReturnTypeElement)

    assertThat(bar2ReturnTypeWrapper.getSimpleName()).isEqualTo("List")

    val bar3ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|3()").getReturnTypeReference()!!
    val bar3ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar3ReturnTypeElement)

    assertThat(bar3ReturnTypeWrapper.getSimpleName()).isEqualTo("List")

    val bar4ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|4()").getReturnTypeReference()!!
    val bar4ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar4ReturnTypeElement)

    assertThat(bar4ReturnTypeWrapper.getSimpleName()).isEqualTo("Map")
  }

  @Test
  fun kotlinPrimitiveTypes() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import kotlin.Int as MyInt

        class Foo {
          fun bar1(): Int = 0
          fun bar2(): kotlin.Int = 0
          fun bar3(): MyInt = 0
        }
        """
          .trimIndent()
      ) as KtFile

    val bar1ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|1()").getReturnTypeReference()!!
    val bar1ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar1ReturnTypeElement)

    assertThat(bar1ReturnTypeWrapper.getSimpleName()).isEqualTo("Int")

    val bar2ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|2()").getReturnTypeReference()!!
    val bar2ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar2ReturnTypeElement)

    assertThat(bar2ReturnTypeWrapper.getSimpleName()).isEqualTo("Int")

    val bar3ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|3()").getReturnTypeReference()!!
    val bar3ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar3ReturnTypeElement)

    assertThat(bar3ReturnTypeWrapper.getSimpleName()).isEqualTo("Int")
  }

  @Test
  fun kotlinStringTypes() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import kotlin.String as MyString

        class Foo {
          fun bar1(): String = 0
          fun bar2(): kotlin.String = 0
          fun bar3(): MyString = 0
        }
        """
          .trimIndent()
      ) as KtFile

    val bar1ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|1()").getReturnTypeReference()!!
    val bar1ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar1ReturnTypeElement)

    assertThat(bar1ReturnTypeWrapper.getSimpleName()).isEqualTo("String")

    val bar2ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|2()").getReturnTypeReference()!!
    val bar2ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar2ReturnTypeElement)

    assertThat(bar2ReturnTypeWrapper.getSimpleName()).isEqualTo("String")

    val bar3ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|3()").getReturnTypeReference()!!
    val bar3ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar3ReturnTypeElement)

    assertThat(bar3ReturnTypeWrapper.getSimpleName()).isEqualTo("String")
  }

  @Test
  fun kotlinArrayTypes() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        class Foo {
          fun bar1(): Array<Bar> {}
          fun bar2(): Array<com.other.Bar> {}
          fun bar3(): IntArray {}
          fun bar4(): Array<Bar<Baz>> {}
        }
        """
          .trimIndent()
      ) as KtFile

    val bar1ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|1()").getReturnTypeReference()!!
    val bar1ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar1ReturnTypeElement)

    assertThat(bar1ReturnTypeWrapper.getSimpleName()).isEqualTo("Array")

    val bar2ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|2()").getReturnTypeReference()!!
    val bar2ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar2ReturnTypeElement)

    assertThat(bar2ReturnTypeWrapper.getSimpleName()).isEqualTo("Array")

    val bar3ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|3()").getReturnTypeReference()!!
    val bar3ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar3ReturnTypeElement)

    assertThat(bar3ReturnTypeWrapper.getSimpleName()).isEqualTo("IntArray")

    val bar4ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("bar|4()").getReturnTypeReference()!!
    val bar4ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(bar4ReturnTypeElement)

    assertThat(bar4ReturnTypeWrapper.getSimpleName()).isEqualTo("Array")
  }

  @Test
  fun kotlinNullableTypes() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        class Foo {
          fun nullable1(): Bar? {}
          fun nullable2(): com.other.Bar? {}
          fun nullable3(): Array<Bar>? {}
          fun nullable4(): List<Bar>? {}
          fun nullable5(): Int? {}
          fun nullable6(): IntArray? {}
        }
        """
          .trimIndent()
      ) as KtFile

    val nullable1ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("nullable|1()").getReturnTypeReference()!!
    val nullable1ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(nullable1ReturnTypeElement)

    assertThat(nullable1ReturnTypeWrapper.getSimpleName()).isEqualTo("Bar")

    val nullable2ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("nullable|2()").getReturnTypeReference()!!
    val nullable2ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(nullable2ReturnTypeElement)

    assertThat(nullable2ReturnTypeWrapper.getSimpleName()).isEqualTo("Bar")

    val nullable3ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("nullable|3()").getReturnTypeReference()!!
    val nullable3ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(nullable3ReturnTypeElement)

    assertThat(nullable3ReturnTypeWrapper.getSimpleName()).isEqualTo("Array")

    val nullable4ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("nullable|4()").getReturnTypeReference()!!
    val nullable4ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(nullable4ReturnTypeElement)

    assertThat(nullable4ReturnTypeWrapper.getSimpleName()).isEqualTo("List")

    val nullable5ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("nullable|5()").getReturnTypeReference()!!
    val nullable5ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(nullable5ReturnTypeElement)

    assertThat(nullable5ReturnTypeWrapper.getSimpleName()).isEqualTo("Int")

    val nullable6ReturnTypeElement =
      myFixture.findParentElement<KtFunction>("nullable|6()").getReturnTypeReference()!!
    val nullable6ReturnTypeWrapper =
      DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(nullable6ReturnTypeElement)

    assertThat(nullable6ReturnTypeWrapper.getSimpleName()).isEqualTo("IntArray")
  }

  @Test
  fun javaUnqualifiedType() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;
        public class Foo {
          public Baz bar() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiTypeElement = myFixture.findParentElement("Ba|z bar()")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun javaUnqualifiedTypeWithImport() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;
        import com.other.Baz;

        public class Foo {
          public Baz bar() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiTypeElement = myFixture.findParentElement("Ba|z bar()")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun javaQualifiedType() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;
        public class Foo {
          public com.other.Baz bar() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiTypeElement = myFixture.findParentElement("Ba|z bar()")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun javaInnerClassType() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;
        import com.other.Baz;

        public class Foo {
          public Baz.InnerClass bar() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiTypeElement = myFixture.findParentElement("Ba|z.InnerClass bar()")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("InnerClass")
  }

  @Test
  fun javaStarImportType() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;
        import com.other.*;

        public class Foo {
          public Baz bar() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiTypeElement = myFixture.findParentElement("Ba|z bar()")
    val wrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element)

    assertThat(wrapper.getSimpleName()).isEqualTo("Baz")
  }

  @Test
  fun javaGenericTypes() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;

        public class Foo {
          public List<Optional<String>> bar1() {}
          public java.util.List<java.util.Optional<java.lang.String>> bar2() {}
          public Map<String, Integer> bar3() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val bar1ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|1()").returnTypeElement!!
    val bar1ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar1ReturnTypeElement)

    assertThat(bar1ReturnTypeWrapper.getSimpleName()).isEqualTo("List")

    val bar2ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|2()").returnTypeElement!!
    val bar2ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar2ReturnTypeElement)

    assertThat(bar2ReturnTypeWrapper.getSimpleName()).isEqualTo("List")

    val bar3ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|3()").returnTypeElement!!
    val bar3ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar3ReturnTypeElement)

    assertThat(bar3ReturnTypeWrapper.getSimpleName()).isEqualTo("Map")
  }

  @Test
  fun javaPrimitiveTypes() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        public class Foo {
          public int bar1() {}
          public Integer bar2() {}
          public java.lang.Integer bar3() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val bar1ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|1()").returnTypeElement!!
    val bar1ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar1ReturnTypeElement)

    assertThat(bar1ReturnTypeWrapper.getSimpleName()).isEqualTo("Integer")

    val bar2ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|2()").returnTypeElement!!
    val bar2ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar2ReturnTypeElement)

    assertThat(bar2ReturnTypeWrapper.getSimpleName()).isEqualTo("Integer")

    val bar3ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|3()").returnTypeElement!!
    val bar3ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar3ReturnTypeElement)

    assertThat(bar3ReturnTypeWrapper.getSimpleName()).isEqualTo("Integer")
  }

  @Test
  fun javaStringTypes() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        public class Foo {
          public String bar1() {}
          public java.lang.String bar2() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val bar1ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|1()").returnTypeElement!!
    val bar1ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar1ReturnTypeElement)

    assertThat(bar1ReturnTypeWrapper.getSimpleName()).isEqualTo("String")

    val bar2ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|2()").returnTypeElement!!
    val bar2ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar2ReturnTypeElement)

    assertThat(bar2ReturnTypeWrapper.getSimpleName()).isEqualTo("String")
  }

  @Test
  fun javaArrayTypes() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        public class Foo {
          public Bar[] bar1() {}
          public com.other.Bar[] bar2() {}
          public Integer[] bar3() {}
          public int[] bar4() {}
          public Bar<Baz>[] bar5() {}
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    val bar1ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|1()").returnTypeElement!!
    val bar1ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar1ReturnTypeElement)

    assertThat(bar1ReturnTypeWrapper.getSimpleName()).isEqualTo("Bar[]")

    val bar2ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|2()").returnTypeElement!!
    val bar2ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar2ReturnTypeElement)

    assertThat(bar2ReturnTypeWrapper.getSimpleName()).isEqualTo("Bar[]")

    val bar3ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|3()").returnTypeElement!!
    val bar3ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar3ReturnTypeElement)

    assertThat(bar3ReturnTypeWrapper.getSimpleName()).isEqualTo("Integer[]")

    val bar4ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|4()").returnTypeElement!!
    val bar4ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar4ReturnTypeElement)

    assertThat(bar4ReturnTypeWrapper.getSimpleName()).isEqualTo("int[]")

    val bar5ReturnTypeElement =
      myFixture.findParentElement<PsiMethod>("bar|5()").returnTypeElement!!
    val bar5ReturnTypeWrapper = DaggerIndexPsiWrapper.JavaFactory(psiFile).of(bar5ReturnTypeElement)

    assertThat(bar5ReturnTypeWrapper.getSimpleName()).isEqualTo("Bar[]")
  }
}
