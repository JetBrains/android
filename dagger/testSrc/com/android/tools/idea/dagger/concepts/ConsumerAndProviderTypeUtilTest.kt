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
package com.android.tools.idea.dagger.concepts

import com.android.tools.idea.dagger.addDaggerAndHiltClasses
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiPrimitiveType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class ConsumerAndProviderTypeUtilTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun typeInsideDaggerWrapper_hasWrappingType() {
    addDaggerAndHiltClasses(myFixture)

    val psiFile =
      myFixture.addFileToProject(
        "src/com/example/Foo.kt",
        // language=kotlin
        """
        package com.example

        import dagger.Lazy
        import javax.inject.Provider

        class Foo

        fun func(
          lazyFoo: Lazy<Foo>,
          providerFoo: Provider<Foo>,
          providerLazyFoo: Provider<Lazy<Foo>>,
          lazyProviderFoo: Lazy<Provider<Foo>>,
          lazyInt: Lazy<Int>,
        )
        """
          .trimIndent()
      )
    myFixture.openFileInEditor(psiFile.virtualFile)

    val lazyFooType = myFixture.findParentElement<KtParameter>("lazy|Foo").psiType!!
    val providerFooType = myFixture.findParentElement<KtParameter>("provider|Foo").psiType!!
    val providerLazyFooType = myFixture.findParentElement<KtParameter>("providerLazy|Foo").psiType!!
    val lazyProviderFooType = myFixture.findParentElement<KtParameter>("lazyProvider|Foo").psiType!!
    val lazyIntType = myFixture.findParentElement<KtParameter>("lazy|Int").psiType!!

    val fooType = myFixture.findParentElement<KtClass>("class F|oo").toPsiType()
    val integerType = PsiPrimitiveType.INT.getBoxedType(/* context= */ psiFile)

    assertThat(lazyFooType.typeInsideDaggerWrapper()).isEqualTo(fooType)
    assertThat(providerFooType.typeInsideDaggerWrapper()).isEqualTo(fooType)
    assertThat(providerLazyFooType.typeInsideDaggerWrapper()).isEqualTo(fooType)

    // Nesting Lazy<Provider<Foo>> is not supported by Dagger (only the opposite order,
    // Provider<Lazy<Foo>>).
    assertThat(lazyProviderFooType.typeInsideDaggerWrapper()).isEqualTo(providerFooType)

    assertThat(lazyIntType.typeInsideDaggerWrapper()).isEqualTo(integerType)
  }

  @Test
  fun typeInsideDaggerWrapper_doesNotHaveWrappingType() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
        package com.example

        class Foo

        fun func(
          foo: Foo,
          integer: Int,
        )
        """
            .trimIndent()
        )
        .virtualFile
    )

    val fooType = myFixture.findParentElement<KtParameter>("fo|o: Foo").psiType!!
    val integerType = myFixture.findParentElement<KtParameter>("inte|ger: Int").psiType!!

    assertThat(fooType.typeInsideDaggerWrapper()).isNull()
    assertThat(fooType.withoutDaggerWrapper()).isEqualTo(fooType)
    assertThat(integerType.typeInsideDaggerWrapper()).isNull()
    assertThat(integerType.withoutDaggerWrapper()).isEqualTo(integerType)
  }
}
