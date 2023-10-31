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
package org.jetbrains.android

import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiNamedElement

class AndroidJavaCompletionContributorTest : AndroidTestCase() {
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