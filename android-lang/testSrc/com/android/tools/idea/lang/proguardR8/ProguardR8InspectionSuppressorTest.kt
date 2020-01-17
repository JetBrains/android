/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.idea.testing.highlightedAs
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction

class ProguardR8InspectionSuppressorTest : ProguardR8TestCase() {

  private fun suppressInspection() {
    val action = myFixture.getIntentionAction("Suppress for statement")
    assertThat(action).isNotNull()
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      action!!.invoke(myFixture.project, myFixture.editor, myFixture.file)
    }
  }

  fun testSuppressionByComment() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class <caret>${"not.existing.Class".highlightedAs(HighlightSeverity.ERROR, "Unresolved class name")}
      """.trimIndent()
    )
    myFixture.checkHighlighting()

    suppressInspection()

    //Suppress above rule with class specification
    myFixture.checkResult(
      """
      #noinspection ShrinkerUnresolvedReference
      -keep class not.existing.Class
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        ${"<caret>-notexistingflag".highlightedAs(HighlightSeverity.ERROR, "Invalid flag")}
      """.trimIndent()
    )
    myFixture.checkHighlighting()

    suppressInspection()

    //Suppress above regular rule
    myFixture.checkResult(
      """
      #noinspection ShrinkerInvalidFlags
      -notexistingflag
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class java.lang.String {
          ${"<caret>notExistingField".highlightedAs(HighlightSeverity.ERROR, "The rule matches no class members")};
        }
      """.trimIndent()
    )
    myFixture.checkHighlighting()

    suppressInspection()

    //Suppress above class member
    myFixture.checkResult(
      """
      -keep class java.lang.String {
        #noinspection ShrinkerUnresolvedReference
        notExistingField;
      }
    """.trimIndent())

    myFixture.checkHighlighting()

    //Suppress few comments above
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class java.lang.String {
        #noinspection ShrinkerUnresolvedReference
        #more comment
        #more comment
        notExistingField;
      }
    """.trimIndent())

    myFixture.checkHighlighting()
  }
}