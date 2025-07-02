/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.ai.transform

import com.android.tools.idea.insights.ai.FixSuggester
import com.android.tools.idea.testing.disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

class CodeTransformationTest {

  @get:Rule val projectRule = ProjectRule()

  @Test
  fun `applying a transformation with no target files throws IllegalStateException`() {
    assertThrows(IllegalStateException::class.java) {
      CodeTransformationImpl(projectRule.project, "", emptyList()).apply()
    }
  }

  @Test
  fun `applying a transformation with no FixSuggester EP throws IllegalStateException`() {
    assertThrows(IllegalStateException::class.java) {
      CodeTransformationImpl(projectRule.project, "", listOf(mock<VirtualFile>())).apply()
    }
  }

  @Test
  fun `apply calls FixSuggester EP`() {
    val fixSuggester = mock<FixSuggester>()
    val file = mock<VirtualFile>()
    ExtensionTestUtil.maskExtensions(
      FixSuggester.EP_NAME,
      listOf(fixSuggester),
      projectRule.disposable,
    )

    CodeTransformationImpl(projectRule.project, "Rename variable a to b", listOf(file)).apply()
    verify(fixSuggester)
      .suggestFix(eq(projectRule.project), eq("Rename variable a to b"), eq(listOf(file)), any())
  }
}
