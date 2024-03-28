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

import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiNamedElement
import org.jetbrains.android.AndroidTestCase

class AndroidDeprecationPresentationCompletionContributorTest : AndroidTestCase() {
  fun testDeprecationFiltersApplied() {
    myFixture.loadNewFile(
      "src/p1/p2/SomeActivity.java",
      // language=JAVA
      """
      package p1.p2;

      import android.text.Html;
      import android.os.Build;

      public class SomeActivity {
        public void foo() {
          /* all API levels */

          if (Build.VERSION.SDK_INT > 24) {
            /* 24+ */
          }
        }
      }
      """
    )

    myFixture.moveCaret("/* all API levels */|")
    myFixture.type("Html.")
    myFixture.completeBasic()

    assertThat(getRelevantLookupElements()).containsExactly(
      "fromHtml(String source, int flags)",
      "fromHtml(String source, int flags, ImageGetter imageGetter, TagHandler tagHandler)",
      // Deprecated since 24:
      "fromHtml(String source)",
      "fromHtml(String source, ImageGetter imageGetter, TagHandler tagHandler)"
    )

    myFixture.moveCaret("/* 24+ */|")
    myFixture.type("Html.")
    myFixture.completeBasic()

    assertThat(getRelevantLookupElements()).containsExactly(
      "fromHtml(String source, int flags)",
      "fromHtml(String source, int flags, ImageGetter imageGetter, TagHandler tagHandler)",
      // Deprecated since 24:
      "fromHtml(String source) [deprecated]",
      "fromHtml(String source, ImageGetter imageGetter, TagHandler tagHandler) [deprecated]"
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

      import android.text.Html
      import android.os.Build

      class SomeActivity {
        fun foo() {
          /* all API levels */

          if (Build.VERSION.SDK_INT > 24) {
            /* 24+ */
          }
        }
      }
      """
    )

    myFixture.moveCaret("/* all API levels */|")
    myFixture.type("Html.")
    myFixture.completeBasic()

    // If this assertion starts failing, it may mean that we have started striking out deprecated
    // items in Kotlin, but that the `isExcluded` functionality of the DeprecationFilter is not
    // correctly implemented. We should fix this, as we will be showing items as struck-out even in
    // contexts where they should not be considered deprecated.
    assertThat(getRelevantLookupElements()).containsExactly(
      "fromHtml(source: String!, flags: Int)",
      "fromHtml(source: String!, flags: Int, imageGetter: Html.ImageGetter!, tagHandler: Html.TagHandler!)",
      "fromHtml(source: String!, flags: Int, imageGetter: ((String!) -> Drawable!)!, tagHandler: ((Boolean, String!, Editable!, XMLReader!) -> Unit)!)",
      // Deprecated since 24:
      "fromHtml(source: String!)",
      "fromHtml(source: String!, imageGetter: Html.ImageGetter!, tagHandler: Html.TagHandler!)",
      "fromHtml(source: String!, imageGetter: ((String!) -> Drawable!)!, tagHandler: ((Boolean, String!, Editable!, XMLReader!) -> Unit)!)",
    )

    myFixture.moveCaret("/* 24+ */|")
    myFixture.type("Html.")
    myFixture.completeBasic()

    // If this assertion starts failing, it may mean that we started striking out deprecated items in
    // completion for Kotlin. If so, the last three items need to have " [deprecated]" appended to them.
    assertThat(getRelevantLookupElements()).containsExactly(
      "fromHtml(source: String!, flags: Int)",
      "fromHtml(source: String!, flags: Int, imageGetter: Html.ImageGetter!, tagHandler: Html.TagHandler!)",
      "fromHtml(source: String!, flags: Int, imageGetter: ((String!) -> Drawable!)!, tagHandler: ((Boolean, String!, Editable!, XMLReader!) -> Unit)!)",
      // Deprecated since 24:
      "fromHtml(source: String!)",
      "fromHtml(source: String!, imageGetter: Html.ImageGetter!, tagHandler: Html.TagHandler!)",
      "fromHtml(source: String!, imageGetter: ((String!) -> Drawable!)!, tagHandler: ((Boolean, String!, Editable!, XMLReader!) -> Unit)!)",
    )
  }

  private fun getRelevantLookupElements(): List<String> {
    return myFixture.lookupElements
      .orEmpty()
      .filter { (it.psiElement as? PsiNamedElement)?.name == "fromHtml" }
      .map {lookupElement ->
        val presentation = LookupElementPresentation()
        lookupElement.renderElement(presentation)
        val text = presentation.itemText!! + presentation.tailText!!
        if (presentation.isStrikeout) "$text [deprecated]" else text
      }
  }
}
