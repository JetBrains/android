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

import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.FakeCodeContextResolver
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class CodeTransformationDeterminerTest {

  @get:Rule val projectRule = ProjectRule()

  private lateinit var codeContextResolver: CodeContextResolver
  private lateinit var determiner: CodeTransformationDeterminer
  private val virtualFiles = mutableListOf(mock<VirtualFile>())

  @Before
  fun setUp() {
    codeContextResolver =
      object : FakeCodeContextResolver(emptyList()) {
        override suspend fun getSourceVirtualFiles(filePath: String): List<VirtualFile> {
          return virtualFiles
        }
      }
    determiner = CodeTransformationDeterminerImpl(projectRule.project, codeContextResolver)
  }

  @Test
  fun `suggest fix with instruction that does not contain extract phrase returns empty transformation`() =
    runBlocking {
      assertThat(determiner.getApplicableTransformation("")).isEqualTo(NoopTransformation)
      assertThat(determiner.getApplicableTransformation("Text text text text"))
        .isEqualTo(NoopTransformation)
    }

  @Test
  fun `suggest fix with instruction that has extract phrase returns correct transformation`():
    Unit = runBlocking {
    val instruction =
      """
          This bug is bad.

          The fix should likely be in AndroidManifest.xml.
        """
        .trimIndent()
    val transformation = determiner.getApplicableTransformation(instruction)

    assertThat(transformation).isInstanceOf(CodeTransformationImpl::class.java)
    transformation as CodeTransformationImpl
    assertThat(transformation.instruction).isEqualTo(instruction)
    assertThat(transformation.files).containsAllIn(virtualFiles).inOrder()
  }

  @Test
  fun `insight with extract phrase but could not match project file returns empty transformation`() =
    runBlocking {
      virtualFiles.clear()
      val instruction =
        """
          This bug is bad.

          The fix should likely be in AndroidManifest.xml.
        """
          .trimIndent()
      val transformation = determiner.getApplicableTransformation(instruction)

      assertThat(transformation).isInstanceOf(NoopTransformation::class.java)
    }
}
