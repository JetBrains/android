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
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.util.parentOfType
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
  fun removeWrappingDaggerType_hasWrappingType() {
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
          lazyInt: Lazy<Int>,
        )
        """
          .trimIndent()
      )
    myFixture.openFileInEditor(psiFile.virtualFile)

    val lazyFooType =
      myFixture.moveCaret("lazy|Foo").parentOfType<KtParameter>(withSelf = true)!!.psiType!!
    val providerFooType =
      myFixture.moveCaret("provider|Foo").parentOfType<KtParameter>(withSelf = true)!!.psiType!!
    val providerLazyFooType =
      myFixture.moveCaret("providerLazy|Foo").parentOfType<KtParameter>(withSelf = true)!!.psiType!!
    val lazyIntType =
      myFixture.moveCaret("lazy|Int").parentOfType<KtParameter>(withSelf = true)!!.psiType!!

    val fooType =
      myFixture.moveCaret("class F|oo").parentOfType<KtClass>(withSelf = true)!!.toPsiType()
    val integerType = PsiPrimitiveType.INT.getBoxedType(/* context= */ psiFile)

    assertThat(lazyFooType.withoutWrappingDaggerType()).isEqualTo(fooType)
    assertThat(providerFooType.withoutWrappingDaggerType()).isEqualTo(fooType)
    assertThat(providerLazyFooType.withoutWrappingDaggerType()).isEqualTo(fooType)

    assertThat(lazyIntType.withoutWrappingDaggerType()).isEqualTo(integerType)
  }

  @Test
  fun removeWrappingDaggerType_doesNotHaveWrappingType() {
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

    val fooType =
      myFixture.moveCaret("fo|o: Foo").parentOfType<KtParameter>(withSelf = true)!!.psiType!!
    val integerType =
      myFixture.moveCaret("inte|ger: Int").parentOfType<KtParameter>(withSelf = true)!!.psiType!!

    assertThat(fooType.withoutWrappingDaggerType()).isEqualTo(fooType)
    assertThat(integerType.withoutWrappingDaggerType()).isEqualTo(integerType)
  }
}
