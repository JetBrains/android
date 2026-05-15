/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerExtension
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private class FakeGeminiPluginApiV2 : GeminiPluginApiV2 {
  var editorQuery: String? = null
  var toolWindowQuery: String? = null
  var editorBlobs: List<LlmBlob> = emptyList()
  var editorPayloads: List<LlmUserQueryPayload> = emptyList()
  var editorTarget: LlmEditorTarget = LlmEditorTarget.NewTab
  var conversationTarget: LlmConversationTarget = LlmConversationTarget.NewConversation

  override suspend fun submitQueryInEditor(
    project: Project,
    query: String,
    blobs: List<LlmBlob>,
    payloads: List<LlmUserQueryPayload>,
    editorTarget: LlmEditorTarget,
    conversationTarget: LlmConversationTarget,
  ): LlmChatInEditorResult {
    this.editorQuery = query
    this.editorBlobs = blobs
    this.editorPayloads = payloads
    this.editorTarget = editorTarget
    this.conversationTarget = conversationTarget
    return LlmChatInEditorResult.Success("conv-id", "editor-tab-id")
  }

  override suspend fun submitQueryInToolWindow(
    project: Project,
    query: String,
    blobs: List<LlmBlob>,
    payloads: List<LlmUserQueryPayload>,
    conversationTarget: LlmConversationTarget,
  ): LlmChatInToolWindowResult {
    this.toolWindowQuery = query
    this.editorBlobs = blobs
    this.editorPayloads = payloads
    this.conversationTarget = conversationTarget
    return LlmChatInToolWindowResult.Success("conv-id")
  }
}

@RunWith(JUnit4::class)
class GeminiPluginApiV2Test : BasePlatformTestCase() {

  @Test
  fun defaultInstance_returnsFallback() = runBlocking {
    val instance = GeminiPluginApiV2.getInstance()
    assertThat(instance).isNotNull()

    val editorResult = instance.submitQueryInEditor(project = project, query = "hi")
    assertThat(editorResult).isInstanceOf(LlmChatInEditorResult.RequestNotSubmitted::class.java)
    assertThat((editorResult as LlmChatInEditorResult.RequestNotSubmitted).reason).isEqualTo(LlmFailureReason.NO_MODELS_AVAILABLE)

    val toolWindowResult = instance.submitQueryInToolWindow(project = project, query = "hi")
    assertThat(toolWindowResult).isInstanceOf(LlmChatInToolWindowResult.RequestNotSubmitted::class.java)
    assertThat((toolWindowResult as LlmChatInToolWindowResult.RequestNotSubmitted).reason).isEqualTo(LlmFailureReason.NO_MODELS_AVAILABLE)
  }

  @Test
  fun customInstance_delegatesCorrectly() = runBlocking {
    val fake = FakeGeminiPluginApiV2()
    ApplicationManager.getApplication().registerExtension(GeminiPluginApiV2.EP_NAME, fake, testRootDisposable)

    val instance = GeminiPluginApiV2.getInstance()
    org.junit.Assert.assertSame(fake, instance)

    val blobs = listOf(LlmBlob("bytes".toByteArray(), "image/png"))
    val payloads = listOf(LlmUserQueryPayload.Reference("ref", 0, 5))

    val editorResult =
      instance.submitQueryInEditor(
        project = project,
        query = "test editor",
        blobs = blobs,
        payloads = payloads,
        editorTarget = LlmEditorTarget.ExistingTab("my-tab-id"),
        conversationTarget = LlmConversationTarget.Existing("existing-conv"),
      )

    assertThat(editorResult).isInstanceOf(LlmChatInEditorResult.Success::class.java)
    val successEditor = editorResult as LlmChatInEditorResult.Success
    assertThat(successEditor.conversationId).isEqualTo("conv-id")
    assertThat(successEditor.editorTabId).isEqualTo("editor-tab-id")

    assertThat(fake.editorQuery).isEqualTo("test editor")
    assertThat(fake.editorBlobs).isEqualTo(blobs)
    assertThat(fake.editorPayloads).isEqualTo(payloads)
    assertThat(fake.editorTarget).isEqualTo(LlmEditorTarget.ExistingTab("my-tab-id"))
    assertThat(fake.conversationTarget).isEqualTo(LlmConversationTarget.Existing("existing-conv"))

    val toolWindowResult =
      instance.submitQueryInToolWindow(
        project = project,
        query = "test tool window",
        blobs = blobs,
        payloads = payloads,
        conversationTarget = LlmConversationTarget.NewConversation,
      )

    assertThat(toolWindowResult).isInstanceOf(LlmChatInToolWindowResult.Success::class.java)
    val successTool = toolWindowResult as LlmChatInToolWindowResult.Success
    assertThat(successTool.conversationId).isEqualTo("conv-id")

    assertThat(fake.toolWindowQuery).isEqualTo("test tool window")
    assertThat(fake.editorBlobs).isEqualTo(blobs)
    assertThat(fake.editorPayloads).isEqualTo(payloads)
    assertThat(fake.conversationTarget).isEqualTo(LlmConversationTarget.NewConversation)
  }
}
