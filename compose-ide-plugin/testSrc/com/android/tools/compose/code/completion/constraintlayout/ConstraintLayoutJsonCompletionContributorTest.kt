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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class ConstraintLayoutJsonCompletionContributorTest {
  // TODO(b/207030860): Change test class to 'LightPlatformCodeInsightFixture4TestCase' once/if we remove the Compose requirement

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setup() {
    StudioFlags.COMPOSE_CONSTRAINTLAYOUT_COMPLETION.override(true)
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
  }

  @After
  fun teardown() {
    StudioFlags.COMPOSE_CONSTRAINTLAYOUT_COMPLETION.clearOverride()
  }

  @Test
  fun completeConstraintSetFields() {
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
  fun completeExtendsValue() {
    @Language("JSON5")
    val content =
      """
      {
        ConstraintSets: {
          start: {
            id1: {},
          },
          end: {
            Extends: '$caret'
          }
        }
      }
    """.trimIndent()
    myFixture.configureByText("myscene.json", content)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings!!).hasSize(1)
    assertThat(myFixture.lookupElementStrings!![0]).isEqualTo("start")
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