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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtParameter
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Ignore // TODO(b/265846405): Start running test when index is enabled
@RunWith(JUnit4::class)
@RunsInEdt
class ProviderDaggerElementTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun getRelatedDaggerElement() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import javax.inject.Inject

          class Foo @Inject constructor() {}

          class Bar @Inject constructor(foo: Foo) {}
          """
            .trimIndent()
        )
        .virtualFile
    )

    val providerPsiElement =
      myFixture
        .moveCaret("Foo @Inject const|ructor")
        .parentOfType<KtConstructor<*>>(withSelf = true)!!
    val providerDaggerElement = ProviderDaggerElement(providerPsiElement)

    val consumerPsiElement =
      myFixture.moveCaret("fo|o: Foo").parentOfType<KtParameter>(withSelf = true)!!
    val consumerDaggerElement = ConsumerDaggerElement(consumerPsiElement)

    assertThat(providerDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(consumerDaggerElement, "Consumers"),
      )
  }
}
