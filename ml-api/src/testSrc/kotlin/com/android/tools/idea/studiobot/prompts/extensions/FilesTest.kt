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
package com.android.tools.idea.studiobot.prompts.extensions

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.Prompt
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.android.tools.idea.studiobot.prompts.impl.PromptImpl
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FilesTest : BasePlatformTestCase() {
  private val mockAiExcludeService: AiExcludeService = mock()
  private val mockStudioBot: StudioBot = mock()

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication()
      .replaceService(StudioBot::class.java, mockStudioBot, testRootDisposable)
    whenever(mockStudioBot.aiExcludeService(project)).thenReturn(mockAiExcludeService)
    whenever(mockStudioBot.isContextAllowed(project)).thenReturn(true)
  }

  @Test
  fun fileContents_psiFile() {
    val contents = "These are the contents of the file!"
    val path = "my/great/file.txt"
    val psiFile = myFixture.addFileToProject(path, contents)
    val virtualFile = psiFile.virtualFile
    val filesUsed = listOf(virtualFile)

    val prompt = buildPrompt(project) { userMessage { fileContents(psiFile) } }

    assertThat(prompt)
      .isEqualTo(
        PromptImpl(
          listOf(
            Prompt.UserMessage(
              listOf(
                Prompt.Message.TextChunk("The contents of the file \"/src/$path\" are:", filesUsed),
                Prompt.Message.CodeChunk(contents, PlainTextLanguage.INSTANCE, filesUsed),
              )
            )
          )
        )
      )
  }

  @Test
  fun fileContents_virtualFile() {
    val contents = "These are the contents of the file!"
    val path = "my/great/file.txt"
    val virtualFile = myFixture.addFileToProject(path, contents).virtualFile
    val filesUsed = listOf(virtualFile)

    val prompt = buildPrompt(project) { userMessage { fileContents(virtualFile) } }

    assertThat(prompt)
      .isEqualTo(
        PromptImpl(
          listOf(
            Prompt.UserMessage(
              listOf(
                Prompt.Message.TextChunk("The contents of the file \"/src/$path\" are:", filesUsed),
                Prompt.Message.CodeChunk(contents, PlainTextLanguage.INSTANCE, filesUsed),
              )
            )
          )
        )
      )
  }

  @Test
  fun openFileContents_selection() {
    val contents = "These are the contents of the file!"
    val path = "my/great/file.txt"
    val selectionStart = 3
    val selectionEnd = 12
    val virtualFile = myFixture.addFileToProject(path, contents).virtualFile
    val filesUsed = listOf(virtualFile)
    ApplicationManager.getApplication().invokeAndWait {
      myFixture.openFileInEditor(virtualFile)
      myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
    }

    val prompt =
      buildPrompt(project) { userMessage { withReadAction { openFileContents(myFixture.editor) } } }

    assertThat(prompt)
      .isEqualTo(
        PromptImpl(
          listOf(
            Prompt.UserMessage(
              listOf(
                Prompt.Message.TextChunk("The file \"/src/$path\" is open.", filesUsed),
                Prompt.Message.TextChunk("The contents before the selected text are:", filesUsed),
                Prompt.Message.CodeChunk(
                  contents.take(selectionStart),
                  PlainTextLanguage.INSTANCE,
                  filesUsed,
                ),
                Prompt.Message.TextChunk("The selected text is:", filesUsed),
                Prompt.Message.CodeChunk(
                  contents.subSequence(selectionStart, selectionEnd).toString(),
                  PlainTextLanguage.INSTANCE,
                  filesUsed,
                ),
                Prompt.Message.TextChunk("The contents after the selected text are:", filesUsed),
                Prompt.Message.CodeChunk(
                  contents.drop(selectionEnd),
                  PlainTextLanguage.INSTANCE,
                  filesUsed,
                ),
              )
            )
          )
        )
      )
  }

  @Test
  fun openFileContents_noSelection() {
    val contents = "These are the contents of the file!"
    val path = "my/great/file.txt"
    val caretOffset = 5
    val virtualFile = myFixture.addFileToProject(path, contents).virtualFile
    val filesUsed = listOf(virtualFile)
    ApplicationManager.getApplication().invokeAndWait {
      myFixture.openFileInEditor(virtualFile)
      myFixture.editor.caretModel.moveToOffset(caretOffset)
    }

    val prompt =
      buildPrompt(project) { userMessage { withReadAction { openFileContents(myFixture.editor) } } }

    assertThat(prompt)
      .isEqualTo(
        PromptImpl(
          listOf(
            Prompt.UserMessage(
              listOf(
                Prompt.Message.TextChunk("The file \"/src/$path\" is open.", filesUsed),
                Prompt.Message.TextChunk("The contents before the caret are:", filesUsed),
                Prompt.Message.CodeChunk(
                  contents.take(caretOffset),
                  PlainTextLanguage.INSTANCE,
                  filesUsed,
                ),
                Prompt.Message.TextChunk("The contents after the caret are:", filesUsed),
                Prompt.Message.CodeChunk(
                  contents.drop(caretOffset),
                  PlainTextLanguage.INSTANCE,
                  filesUsed,
                ),
              )
            )
          )
        )
      )
  }
}
