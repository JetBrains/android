/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ai

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.FakeCodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.Language
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GeminiToolkitTest {

  @get:Rule val projectRule = ProjectRule()

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setUp() {
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )
  }

  @Test
  fun `test is gemini enabled`() {
    val toolKit = GeminiToolkitImpl(projectRule.project)

    fakeGeminiPluginApi.available = false
    assertThat(toolKit.isGeminiEnabled).isEqualTo(false)

    fakeGeminiPluginApi.available = true
    assertThat(toolKit.isGeminiEnabled).isEqualTo(true)
  }

  @Test
  fun `code context resolver returns empty result when context sharing is off`() = runBlocking {
    projectRule.project.replaceService(
      CodeContextResolver::class.java,
      FakeCodeContextResolver(listOf(CodeContext("class", "a/b/c", "blah", Language.KOTLIN))),
      projectRule.disposable,
    )
    val toolKit = GeminiToolkitImpl(projectRule.project)

    fakeGeminiPluginApi.contextAllowed = false
    assertThat(toolKit.getSource(StacktraceGroup())).isEqualTo(CodeContextData.UNASSIGNED)

    fakeGeminiPluginApi.contextAllowed = true
    assertThat(toolKit.getSource(StacktraceGroup()).codeContext).isNotEmpty()
  }
}
