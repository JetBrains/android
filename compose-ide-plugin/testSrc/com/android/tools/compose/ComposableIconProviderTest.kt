/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.compose

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Iconable
import com.intellij.ui.RowIcon
import com.intellij.util.PsiIconUtil
import icons.StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposableIconProviderTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setup() {
    // Allow @Composable attribute to be used in snippets below.
    projectRule.fixture.addFileToProject("androidx/compose/runtime/Composable.kt", """
package androidx.compose.runtime

annotation class Composable""")
  }

  @Test
  fun getPresentation_notAFunction() {
    projectRule.fixture.configureByText(KotlinFileType.INSTANCE, """
package com.example

val fo<caret>o = 1234

""")

    runReadAction {
      val element = projectRule.fixture.elementAtCaret
      val icon = PsiIconUtil.getProvidersIcon(element, 0)
      assertThat(icon).isNotNull()
      assertThat(icon).isNotEqualTo(COMPOSABLE_FUNCTION)
    }
  }

  @Test
  fun getPresentation_notComposeFunction() {
    projectRule.fixture.configureByText(KotlinFileType.INSTANCE, """
package com.example

fun testFun<caret>ction() {}

""")

    runReadAction {
      val element = projectRule.fixture.elementAtCaret
      val icon = PsiIconUtil.getProvidersIcon(element, 0)
      assertThat(icon).isNotNull()
      assertThat(icon).isNotEqualTo(COMPOSABLE_FUNCTION)
    }
  }

  @Test
  fun getPresentation_composeFunctionWithVisibility() {
    projectRule.fixture.configureByText(KotlinFileType.INSTANCE, """
package com.example

import androidx.compose.runtime.Composable

@Composable
fun testFun<caret>ction() {}

""")

    runReadAction {
      val element = projectRule.fixture.elementAtCaret

      val icon = PsiIconUtil.getProvidersIcon(element, Iconable.ICON_FLAG_VISIBILITY)

      assertThat(icon).isInstanceOf(RowIcon::class.java)
      val rowIcon = icon as RowIcon
      assertThat(rowIcon.iconCount).isEqualTo(2)
      assertThat(rowIcon.getIcon(0)).isEqualTo(COMPOSABLE_FUNCTION)
      assertThat(rowIcon.getIcon(1)).isEqualTo(AllIcons.Nodes.C_public)
    }
  }

  @Test
  fun getPresentation_composeFunctionWithoutVisibility() {
    projectRule.fixture.configureByText(KotlinFileType.INSTANCE, """
package com.example

import androidx.compose.runtime.Composable

@Composable
fun testFun<caret>ction() {}

""")

    runReadAction {
      val element = projectRule.fixture.elementAtCaret

      val icon = PsiIconUtil.getProvidersIcon(element, 0)
      assertThat(icon).isEqualTo(COMPOSABLE_FUNCTION)
    }
  }
}
