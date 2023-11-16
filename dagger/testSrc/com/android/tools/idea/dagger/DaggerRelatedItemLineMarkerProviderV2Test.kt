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
package com.android.tools.idea.dagger

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.dagger.DaggerRelatedItemLineMarkerProviderV2.Companion.canReceiveLineMarker
import com.android.tools.idea.dagger.DaggerRelatedItemLineMarkerProviderV2.Companion.getGotoItems
import com.android.tools.idea.dagger.concepts.DaggerElement
import com.android.tools.idea.dagger.concepts.DaggerRelatedElement
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DaggerRelatedItemLineMarkerProviderV2Test {

  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val myFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Test
  fun canReceiveLineMarker_kotlin() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      class Foo constructor() {
        val property: Int = 0
      }
      """
        .trimIndent()
    )

    assertThat(myFixture.moveCaret("cla|ss Foo constructor").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("class Fo|o constructor").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.findParentElement<KtClass>("class Fo|o constructor").canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("class Foo constr|uctor").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.findParentElement<KtFunction>("class Foo constr|uctor").canReceiveLineMarker()
      )
      .isFalse()

    assertThat(myFixture.moveCaret("va|l property: Int = 0").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("val prop|erty: Int = 0").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.findParentElement<KtProperty>("val prop|erty: Int = 0").canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("val property: In|t = 0").canReceiveLineMarker()).isTrue()
    assertThat(myFixture.moveCaret("val property: Int = |0").canReceiveLineMarker()).isFalse()
  }

  @Test
  fun canReceiveLineMarker_java() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      public class Foo {
        public Foo() {}

        private int property = 0;
      }
      """
        .trimIndent()
    )

    assertThat(myFixture.moveCaret("cla|ss Foo").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("class Fo|o").canReceiveLineMarker()).isTrue()
    assertThat(myFixture.findParentElement<PsiClass>("class Fo|o").canReceiveLineMarker()).isFalse()

    assertThat(myFixture.moveCaret("pub|lic Foo()").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("public Fo|o()").canReceiveLineMarker()).isTrue()
    assertThat(myFixture.findParentElement<PsiMethod>("public Fo|o()").canReceiveLineMarker())
      .isFalse()

    assertThat(myFixture.moveCaret("pri|vate int property = 0;").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("private in|t property = 0;").canReceiveLineMarker()).isFalse()
    assertThat(myFixture.moveCaret("private int pro|perty = 0;").canReceiveLineMarker()).isTrue()
    assertThat(
        myFixture.findParentElement<PsiField>("private int pro|perty = 0;").canReceiveLineMarker()
      )
      .isFalse()
    assertThat(myFixture.moveCaret("private int property = |0;").canReceiveLineMarker()).isFalse()
  }

  @Test
  fun gotoItemOrdering() {
    val mockRelatedElements =
      listOf(
        DaggerRelatedElement(
          createMockDaggerElement("ElementName8"),
          "Consumers",
          "navigate.to.consumer",
          "CustomName8"
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName7"),
          "Providers",
          "navigate.to.provider",
          "CustomName7"
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName4"),
          "Consumers",
          "navigate.to.consumer",
          null
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName3"),
          "Providers",
          "navigate.to.provider",
          null
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName6"),
          "Consumers",
          "navigate.to.consumer",
          "CustomName6"
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName5"),
          "Providers",
          "navigate.to.provider",
          "CustomName5"
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName2"),
          "Consumers",
          "navigate.to.consumer",
          null
        ),
        DaggerRelatedElement(
          createMockDaggerElement("ElementName1"),
          "Providers",
          "navigate.to.provider",
          null
        ),
      )

    val mockDaggerElement: DaggerElement = mock()
    whenever(mockDaggerElement.getRelatedDaggerElements()).thenReturn(mockRelatedElements)

    val gotoItems = mockDaggerElement.getGotoItems()

    // Items are sorted at a higher level by their group.
    assertThat(gotoItems.map { it.group })
      .containsExactlyElementsIn(List(4) { "Consumers" } + List(4) { "Providers" })
      .inOrder()

    // Within the group, items are sorted by their names. We can't directly verify the display text
    // since it's controlled by the platform, but we can validate that the goto items contain the
    // associated PsiElements that we expect in the correct order.
    assertThat(gotoItems.map { it.element })
      .containsExactly(
        mockRelatedElements[4].relatedElement.psiElement, // CustomName6 (Consumers)
        mockRelatedElements[0].relatedElement.psiElement, // CustomName8 (Consumers)
        mockRelatedElements[6].relatedElement.psiElement, // ElementName2 (Consumers)
        mockRelatedElements[2].relatedElement.psiElement, // ElementName4 (Consumers)
        mockRelatedElements[5].relatedElement.psiElement, // CustomName5 (Providers)
        mockRelatedElements[1].relatedElement.psiElement, // CustomName7 (Providers)
        mockRelatedElements[7].relatedElement.psiElement, // ElementName1 (Providers)
        mockRelatedElements[3].relatedElement.psiElement, // ElementName3 (Providers)
      )
      .inOrder()
  }

  private fun createMockDaggerElement(elementText: String): DaggerElement {
    val psiElement = myFixture.addClass("class $elementText {}")
    val mockDaggerElement: DaggerElement = mock()
    whenever(mockDaggerElement.psiElement).thenReturn(psiElement)

    return mockDaggerElement
  }
}
