/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.completion

import com.android.tools.idea.testing.deleteText
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestCase

class AndroidDeprecationPresentationCompletionContributorTest : AndroidTestCase() {
  fun testDeprecationFiltersApplied() {
    myFixture.loadNewFile(
      "src/p1/p2/SomeActivity.java",
      // language=JAVA
      """
      package p1.p2;

      import android.os.Build;
      import android.os.Bundle;

      public class SomeActivity {
        public void foo() {
          /* all API levels */

          if (Build.VERSION.SDK_INT > 33) {
            /* 33+ */
          }
        }
      }
      """
    )

    myFixture.moveCaret("/* all API levels */|")
    assertThat(typeAndGetCompletions("new Bundle().getParcel", "getParcelable")).containsExactly(
      "getParcelable(String key, Class<T> clazz)",
      // Deprecated since 33:
      "getParcelable(String key)",
    )

    myFixture.moveCaret("/* 33+ */|")
    assertThat(typeAndGetCompletions("new Bundle().getParcel", "getParcelable")).containsExactly(
      "getParcelable(String key, Class<T> clazz)",
      // Deprecated since 33:
      "getParcelable(String key) [deprecated]",
    )
  }

  // This contributor actually should not do anything in Kotlin currently, because we don't
  // strikeout deprecated items. But if we start doing that, then we will need to fix this.
  // This test exists mostly, to make sure that the contributor isn't currently interfering
  // with the results and passing them through as normal. It also checks that if we ever
  // start striking out deprecated items in Kotlin, that the filters also implement the
  // functionality to let us filter them out in contexts where they are not deprecated.
  fun testDeprecationFiltersApplied_kotlin() {
    myFixture.loadNewFile(
      "src/p1/p2/SomeActivity.kt",
      // language=kotlin
      """
      package p1.p2

      import android.os.Build
      import android.os.Bundle

      class SomeActivity {
        fun foo() {
          /* all API levels */

          if (Build.VERSION.SDK_INT > 33) {
            /* 33+ */
          }
        }
      }
      """
    )

    myFixture.moveCaret("/* all API levels */|")

    // If this assertion starts failing, it may mean that we have started striking out deprecated
    // items in Kotlin, but that the `isExcluded` functionality of the DeprecationFilter is not
    // correctly implemented. We should fix this, as we will be showing items as struck-out even in
    // contexts where they should not be considered deprecated.
    assertThat(typeAndGetCompletions("Bundle().getParcel", "getParcelable")).containsExactly(
      "getParcelable(key: String?, clazz: Class<T!>)",
      // Deprecated since 33:
      "getParcelable(key: String?)",
    )

    myFixture.moveCaret("/* 33+ */|")
    // If this assertion starts failing, it may mean that we started striking out deprecated items in
    // completion for Kotlin. If so, the last item needs to have " [deprecated]" appended to it.
    assertThat(typeAndGetCompletions("Bundle().getParcel", "getParcelable")).containsExactly(
      "getParcelable(key: String?, clazz: Class<T!>)",
      // Deprecated since 33:
      "getParcelable(key: String?)",
    )
  }

  private fun typeAndGetCompletions(prefixToType: String, nameFilter: String): List<String> {
    try {
      myFixture.type(prefixToType)
      myFixture.completeBasic()
      return getRelevantLookupElements(nameFilter)
    } finally {
      // delete typed text
      WriteCommandAction.runWriteCommandAction(project) {
        with(myFixture.editor) {
          val offset = caretModel.primaryCaret.offset
          document.deleteString(offset - prefixToType.length, offset)
        }
      }
    }
  }

  private fun getRelevantLookupElements(name: String): List<String> {
    return myFixture.lookupElements
      .orEmpty()
      .filter { (it.psiElement as? PsiNamedElement)?.name == name }
      .map {lookupElement ->
        val presentation = LookupElementPresentation()
        lookupElement.renderElement(presentation)
        val text = presentation.itemText!! + presentation.tailText!!
        if (presentation.isStrikeout) "$text [deprecated]" else text
      }
  }
}
