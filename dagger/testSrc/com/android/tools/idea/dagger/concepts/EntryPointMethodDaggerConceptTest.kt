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
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class EntryPointMethodDaggerConceptTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val myFixture by lazy { projectRule.fixture }
  private val myProject by lazy { myFixture.project }

  @Test
  fun indexer() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example
        import dagger.hilt.EntryPoint

        @EntryPoint
        interface MyEntryPoint {
          fun bar1(): Bar
          fun bar2(): Bar
          fun barWithArgument(arg: Int): Bar
          fun functionWithoutType() = 3
        }

        interface NotAnEntryPoint {
          fun bar1(): Bar
          fun bar2(): Bar
        }
        """
          .trimIndent()
      ) as KtFile

    val indexResults = EntryPointMethodDaggerConcept.indexers.runIndexerOn(psiFile)

    assertThat(indexResults)
      .containsExactly(
        "Bar",
        setOf(
          EntryPointMethodIndexValue(MY_ENTRY_POINT_ID, "bar1"),
          EntryPointMethodIndexValue(MY_ENTRY_POINT_ID, "bar2")
        )
      )
  }

  @Test
  fun entryPointMethodIndexValue_serialization() {
    val indexValue = EntryPointMethodIndexValue(MY_ENTRY_POINT_ID, "def")
    assertThat(serializeAndDeserializeIndexValue(indexValue)).isEqualTo(indexValue)
  }

  @Test
  fun entryPointMethodIndexValue_resolveToDaggerElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example
      import dagger.hilt.EntryPoint

      @EntryPoint
      interface MyEntryPoint {
        fun bar1(): Bar
        fun bar2(): Bar
        fun barWithArgument(arg: Int): Bar
        fun functionWithoutType() = 3
      }

      interface NotAnEntryPoint {
        fun bar3(): Bar
        fun bar4(): Bar
      }
      """
        .trimIndent()
    )

    val bar1DaggerElement =
      EntryPointMethodDaggerElement(myFixture.findParentElement<KtFunction>("fun ba|r1(): Bar"))
    val bar2DaggerElement =
      EntryPointMethodDaggerElement(myFixture.findParentElement<KtFunction>("fun ba|r2(): Bar"))

    // Expected to resolve
    assertThat(
        EntryPointMethodIndexValue(MY_ENTRY_POINT_ID, "bar1")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(bar1DaggerElement)

    assertThat(
        EntryPointMethodIndexValue(MY_ENTRY_POINT_ID, "bar2")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(bar2DaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        MY_ENTRY_POINT_ID to "barWithArgument",
        MY_ENTRY_POINT_ID to "functionWithoutType",
        NOT_AN_ENTRY_POINT_ID to "bar3",
        NOT_AN_ENTRY_POINT_ID to "bar4",
      )

    for ((classId, methodName) in nonResolving) {
      assertThat(
          EntryPointMethodIndexValue(classId, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope())
        )
        .isEmpty()
    }
  }

  @Test
  fun entryPointMethodIndexValue_resolveToDaggerElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;
      import dagger.hilt.EntryPoint;

      @EntryPoint
      interface MyEntryPoint {
        Bar bar1();
        Bar bar2();
        Bar barWithArgument(int arg);
      }

      interface NotAnEntryPoint {
        Bar bar3();
        Bar bar4();
      }
      """
        .trimIndent()
    )

    val bar1DaggerElement =
      EntryPointMethodDaggerElement(myFixture.findParentElement<PsiMethod>("Bar ba|r1();"))
    val bar2DaggerElement =
      EntryPointMethodDaggerElement(myFixture.findParentElement<PsiMethod>("Bar ba|r2();"))

    // Expected to resolve
    assertThat(
        EntryPointMethodIndexValue(MY_ENTRY_POINT_ID, "bar1")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(bar1DaggerElement)

    assertThat(
        EntryPointMethodIndexValue(MY_ENTRY_POINT_ID, "bar2")
          .resolveToDaggerElements(myProject, myProject.projectScope())
      )
      .containsExactly(bar2DaggerElement)

    // Expected to not resolve
    val nonResolving =
      listOf(
        MY_ENTRY_POINT_ID to "barWithArgument",
        NOT_AN_ENTRY_POINT_ID to "bar3",
        NOT_AN_ENTRY_POINT_ID to "bar4",
      )

    for ((classId, methodName) in nonResolving) {
      assertThat(
          EntryPointMethodIndexValue(classId, methodName)
            .resolveToDaggerElements(myProject, myProject.projectScope())
        )
        .isEmpty()
    }
  }

  @Test
  fun entryPointMethodDaggerElement_getRelatedDaggerElements() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;
      import dagger.hilt.EntryPoint;
      import javax.inject.Inject;

      @EntryPoint
      interface MyEntryPoint {
        Bar bar1();
        Bar bar2();
      }

      class Bar {
        @Inject
        public Bar() {}
      }
      """
        .trimIndent()
    )

    val bar1EntryPointDaggerElement =
      EntryPointMethodDaggerElement(myFixture.findParentElement<PsiMethod>("Bar ba|r1();"))
    val bar2EntryPointDaggerElement =
      EntryPointMethodDaggerElement(myFixture.findParentElement<PsiMethod>("Bar ba|r2();"))

    val barProviderDaggerElement =
      ProviderDaggerElement(myFixture.findParentElement<PsiMethod>("public Ba|r()"))

    assertThat(bar1EntryPointDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          barProviderDaggerElement,
          "Providers",
          "navigate.to.provider.from.component"
        )
      )

    assertThat(bar2EntryPointDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          barProviderDaggerElement,
          "Providers",
          "navigate.to.provider.from.component"
        )
      )

    assertThat(barProviderDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          bar1EntryPointDaggerElement,
          "Exposed by entry points",
          "navigate.to.component.exposes",
          "MyEntryPoint"
        ),
        DaggerRelatedElement(
          bar2EntryPointDaggerElement,
          "Exposed by entry points",
          "navigate.to.component.exposes",
          "MyEntryPoint"
        )
      )
  }

  companion object {
    private val MY_ENTRY_POINT_ID = ClassId.fromString("com/example/MyEntryPoint")
    private val NOT_AN_ENTRY_POINT_ID = ClassId.fromString("com/example/NotAnEntryPoint")
  }
}
