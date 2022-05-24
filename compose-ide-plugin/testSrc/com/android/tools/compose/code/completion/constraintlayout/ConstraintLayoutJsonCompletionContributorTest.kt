/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.code.completion.constraintlayout

import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.lang.annotations.Language
import org.junit.Test

internal class ConstraintLayoutJsonCompletionContributorTest : LightPlatformCodeInsightFixture4TestCase() {

  @Test
  fun completeConstraintSetIds() {
    @Language("JSON5")
    val content =
      """
      {
        ConstraintSets: {
          start: {
            id1: {},
            id2: {},
            id3: {}
          },
          end: {
            id1: {},
            i$caret
          }
        }
      }
    """.trimIndent()
    myFixture.configureByText("myscene.json", content)
    myFixture.completeBasic()
    val items = myFixture.lookupElementStrings!!
    assertThat(items).hasSize(2)
    assertThat((items[0])).isEqualTo("id2")
    assertThat((items[1])).isEqualTo("id3")
  }

  @Test
  fun completionHandlerResult() {
    @Language("JSON5")
    val content =
      """
      {
        ConstraintSets: {
          start: {
            id1: {},
          },
          end: {
            i$caret
          }
        }
      }
    """.trimIndent()
    myFixture.configureByText("myscene.json", content)
    myFixture.completeBasic()
    myFixture.checkResult(
      //language=JSON5
      """{
  ConstraintSets: {
    start: {
      id1: {},
    },
    end: {
      id1: {
        $caret
      }
    }
  }
}""")

    myFixture.configureByText(
      "myscene2.json",
      // language=JSON5
      """
      {
        ConstraintSets: {
          start: {
            Ext$caret
          }
        }
      }
    """.trimIndent())
    myFixture.completeBasic()
    myFixture.checkResult(
      // language=JSON5
      """{
  ConstraintSets: {
    start: {
      Extends: '$caret',
    }
  }
}""")
  }
}