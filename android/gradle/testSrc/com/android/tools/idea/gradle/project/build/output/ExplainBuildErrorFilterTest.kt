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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.studiobot.ChatService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.Prompt
import com.android.tools.idea.testing.disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ExplainBuildErrorFilterTest {

  @Rule
  @JvmField
  val projectRule = ProjectRule()

  private val chatService =
    object : ChatService.StubChatService() {

      var lastQuery: String? = null
      var lastSource: StudioBot.RequestSource? = null
      var sendQueryInvoked: Boolean = false

      override fun stageChatQuery(prompt: String, requestSource: StudioBot.RequestSource) {
        lastQuery = prompt
        lastSource = requestSource
      }

      override fun sendChatQuery(prompt: Prompt, requestSource: StudioBot.RequestSource, displayText: String?) {
        lastQuery = prompt.messages.first().toString()
        lastSource = requestSource
        sendQueryInvoked = true
      }
    }

  private val studioBot =
    object : StudioBot.StubStudioBot() {
      var isContextSharingAllowed: Boolean = false
      override fun isContextAllowed(project: Project): Boolean = isContextSharingAllowed
      override fun chat(project: Project): ChatService = chatService
    }

  @Before
  fun setup() {
    ApplicationManager.getApplication()
      .replaceService(StudioBot::class.java, studioBot, projectRule.disposable)
  }

  /** Regression test for b/323135834. */
  @Test
  fun `check ask studio bot links`() {
    val filter = ExplainBuildErrorFilter()
    val output =
      """
      e: file:///Users/someone/AndroidStudioProjects/MyApplication31/app/build.gradle.kts:11:24: Expecting an element

      >> Ask Gemini Expecting an element
    """
        .trimIndent()
    studioBot.isContextSharingAllowed = false
    val result = filter.applyFilter(output, output.length)!!.resultItems[0]
    assertEquals(output.indexOf(">> Ask Gem"), result.highlightStartOffset)
    assertEquals(output.lastIndexOf(" Expecting an"), result.highlightEndOffset)
    result.hyperlinkInfo!!.navigate(projectRule.project)
    assertEquals("Explain build error: Expecting an element", chatService.lastQuery)
    assertEquals(StudioBot.RequestSource.BUILD, chatService.lastSource)
    assertEquals(false, chatService.sendQueryInvoked)
  }

  @Test
  fun `send query immediately if context sharing allowed`() {
    val filter = ExplainBuildErrorFilter()
    val output =
      """
      e: file:///Users/someone/AndroidStudioProjects/MyApplication31/app/build.gradle.kts:11:24: Expecting an element

      >> Ask Gemini Expecting an element
    """
        .trimIndent()
    studioBot.isContextSharingAllowed = true
    val result = filter.applyFilter(output, output.length)!!.resultItems[0]
    assertEquals(output.indexOf(">> Ask Gem"), result.highlightStartOffset)
    assertEquals(output.lastIndexOf(" Expecting an"), result.highlightEndOffset)
    result.hyperlinkInfo!!.navigate(projectRule.project)
    assertEquals("UserMessage(chunks=[TextChunk(text=Explain build error: Expecting an element, filesUsed=[])])", chatService.lastQuery)
    assertEquals(StudioBot.RequestSource.BUILD, chatService.lastSource)
    assertEquals(true, chatService.sendQueryInvoked)
  }
}
