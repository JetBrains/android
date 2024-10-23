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
package com.android.tools.idea.gemini

import com.android.tools.idea.studiobot.AiExcludeException
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerExtension
import io.ktor.util.reflect.instanceOf
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class FakeGeminiPluginApi : GeminiPluginApi {
  var contextAllowed = true
  var fileExcluded = false
  var sentPrompt: LlmPrompt? = null

  override val MAX_QUERY_CHARS = Int.MAX_VALUE

  override fun isContextAllowed(project: Project) = contextAllowed

  override fun isFileExcluded(project: Project, file: VirtualFile) = fileExcluded

  override fun sendChatQuery(
    project: Project,
    prompt: LlmPrompt,
    displayText: String?,
    requestSource: GeminiPluginApi.RequestSource,
  ) {
    sentPrompt = prompt
  }

  override fun stageChatQuery(
    project: Project,
    prompt: String,
    requestSource: GeminiPluginApi.RequestSource,
  ) {}
}

@RunWith(JUnit4::class)
class LlmPromptBuilderTest : BasePlatformTestCase() {
  private lateinit var geminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setup() {
    geminiPluginApi = FakeGeminiPluginApi()
    ApplicationManager.getApplication()
      .registerExtension(GeminiPluginApi.EP_NAME, geminiPluginApi, testRootDisposable)
  }

  @Test
  fun buildLlmPrompt_nominal() {
    val prompt =
      buildLlmPrompt(project) {
        systemMessage { text("You are Gemini.", emptyList()) }
        userMessage { text("Hi!", emptyList()) }
        modelMessage { text("How can I help you?", emptyList()) }
        userMessage { text("What is Compose?", emptyList()) }
      }

    assertThat(prompt.formatForTests())
      .isEqualTo(
        """
      |SYSTEM
      |You are Gemini.
      |
      |USER
      |Hi!
      |
      |MODEL
      |How can I help you?
      |
      |USER
      |What is Compose?
    """
          .trimMargin()
          .trim()
      )
  }

  @Test
  fun buildLlmPrompt_withCode() {
    val prompt =
      buildLlmPrompt(project) {
        systemMessage { text("You are Gemini.", emptyList()) }
        userMessage {
          text("Explain this code", emptyList())
          code("fun foo() = 42", KotlinLanguage.INSTANCE, emptyList())
        }
      }

    assertThat(prompt.formatForTests())
      .isEqualTo(
        """
      |SYSTEM
      |You are Gemini.
      |
      |USER
      |Explain this code
      |```kotlin
      |fun foo() = 42
      |```
    """
          .trimMargin()
          .trim()
      )
  }

  @Test
  fun buildLlmPrompt_enforcesContextSharing() {
    val f1 = myFixture.addFileToProject("file1.txt", "hi").virtualFile
    geminiPluginApi.contextAllowed = false

    try {
      buildLlmPrompt(project) { userMessage { text("hi", listOf(f1)) } }
      fail("buildLlmPrompt should've failed since context sharing is not enabled")
    } catch (e: Exception) {
      assertThat(e).instanceOf(AiExcludeException::class)
      assertThat(e.message)
        .isEqualTo(
          "User has not enabled context sharing. This setting must be checked before building a prompt that used any files as context."
        )
    }
  }

  @Test
  fun buildLlmPrompt_enforcesAiExclude() {
    val f1 = myFixture.addFileToProject("file1.txt", "hi").virtualFile
    geminiPluginApi.fileExcluded = true

    try {
      buildLlmPrompt(project) { userMessage { text("hi", listOf(f1)) } }
      fail("buildLlmPrompt should've failed since file f1 is aiexcluded")
    } catch (e: Exception) {
      assertThat(e).instanceOf(AiExcludeException::class)
      assertThat(e.message).contains("file1.txt")
    }
  }
}
