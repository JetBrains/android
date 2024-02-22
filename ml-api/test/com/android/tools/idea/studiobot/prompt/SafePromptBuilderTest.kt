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
package com.android.tools.idea.studiobot.prompt

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.studiobot.AiExcludeException
import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.MalformedPromptException
import com.android.tools.idea.studiobot.prompts.SafePrompt
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.android.tools.idea.studiobot.prompts.impl.SafePromptImpl
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import kotlin.test.assertFailsWith
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SafePromptBuilderTest : BasePlatformTestCase() {
  private val mockAiExcludeService: AiExcludeService = mock()
  private val mockStudioBot: StudioBot = mock()

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication()
      .replaceService(StudioBot::class.java, mockStudioBot, testRootDisposable)
    whenever(mockStudioBot.aiExcludeService()).thenReturn(mockAiExcludeService)
  }

  @Test
  fun buildPrompt_completePrompt() {
    val prompt =
      buildPrompt(project) {
        systemMessage {
          text("You are Studio Bot, an AI assistant for Android Studio.", emptyList())
        }
        userMessage { text("Hello Studio Bot!", emptyList()) }
        modelMessage { text("Hello! How are you?", emptyList()) }
        userMessage { text("I am doing well, how about you?", emptyList()) }
      }
    assertThat(prompt)
      .isEqualTo(
        SafePromptImpl(
          listOf(
            SafePrompt.SystemMessage(
              listOf(
                SafePrompt.Message.TextChunk(
                  "You are Studio Bot, an AI assistant for Android Studio.",
                  emptyList(),
                )
              )
            ),
            SafePrompt.UserMessage(
              listOf(SafePrompt.Message.TextChunk("Hello Studio Bot!", emptyList()))
            ),
            SafePrompt.ModelMessage(
              listOf(SafePrompt.Message.TextChunk("Hello! How are you?", emptyList()))
            ),
            SafePrompt.UserMessage(
              listOf(SafePrompt.Message.TextChunk("I am doing well, how about you?", emptyList()))
            ),
          )
        )
      )
  }

  @Test
  fun buildPrompt_promptWithCode() {
    val prompt =
      buildPrompt(project) {
        userMessage { text("Write some Kotlin code.", emptyList()) }
        modelMessage {
          code(
            """
            fun f(): Int {
              return 5
            }
            """
              .trimIndent(),
            KotlinLanguage.INSTANCE,
            emptyList(),
          )
        }
        userMessage {
          text("Does this Java code do the same thing?", emptyList())
          code(
            """
            int f() {
              return 5;
            }
            """
              .trimIndent(),
            JavaLanguage.INSTANCE,
            emptyList(),
          )
        }
      }
    assertThat(prompt)
      .isEqualTo(
        SafePromptImpl(
          listOf(
            SafePrompt.UserMessage(
              listOf(SafePrompt.Message.TextChunk("Write some Kotlin code.", emptyList()))
            ),
            SafePrompt.ModelMessage(
              listOf(
                SafePrompt.Message.CodeChunk(
                  """
                  fun f(): Int {
                    return 5
                  }
                  """
                    .trimIndent(),
                  KotlinLanguage.INSTANCE,
                  emptyList(),
                )
              )
            ),
            SafePrompt.UserMessage(
              listOf(
                SafePrompt.Message.TextChunk("Does this Java code do the same thing?", emptyList()),
                SafePrompt.Message.CodeChunk(
                  """
                  int f() {
                    return 5;
                  }
                  """
                    .trimIndent(),
                  JavaLanguage.INSTANCE,
                  emptyList(),
                ),
              )
            ),
          )
        )
      )
  }

  @Test
  fun buildPrompt_fileContents_psiFile() {
    val contents = "These are the contents of the file!"
    val path = "my/great/file.txt"
    val psiFile = myFixture.addFileToProject(path, contents)
    val virtualFile = psiFile.virtualFile
    val filesUsed = listOf(virtualFile)

    val prompt = buildPrompt(project) { userMessage { fileContents(psiFile) } }

    assertThat(prompt)
      .isEqualTo(
        SafePromptImpl(
          listOf(
            SafePrompt.UserMessage(
              listOf(
                SafePrompt.Message.TextChunk(
                  "The contents of the file \"/src/$path\" are:",
                  filesUsed,
                ),
                SafePrompt.Message.CodeChunk(contents, PlainTextLanguage.INSTANCE, filesUsed),
              )
            )
          )
        )
      )
  }

  @Test
  fun buildPrompt_fileContents_virtualFile() {
    val contents = "These are the contents of the file!"
    val path = "my/great/file.txt"
    val virtualFile = myFixture.addFileToProject(path, contents).virtualFile
    val filesUsed = listOf(virtualFile)

    val prompt = buildPrompt(project) { userMessage { fileContents(virtualFile) } }

    assertThat(prompt)
      .isEqualTo(
        SafePromptImpl(
          listOf(
            SafePrompt.UserMessage(
              listOf(
                SafePrompt.Message.TextChunk(
                  "The contents of the file \"/src/$path\" are:",
                  filesUsed,
                ),
                SafePrompt.Message.CodeChunk(contents, PlainTextLanguage.INSTANCE, filesUsed),
              )
            )
          )
        )
      )
  }

  @Test
  fun buildPrompt_openFileContents_selection() {
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
        SafePromptImpl(
          listOf(
            SafePrompt.UserMessage(
              listOf(
                SafePrompt.Message.TextChunk("The file \"/src/$path\" is open.", filesUsed),
                SafePrompt.Message.TextChunk(
                  "The contents before the selected text are:",
                  filesUsed,
                ),
                SafePrompt.Message.CodeChunk(
                  contents.take(selectionStart),
                  PlainTextLanguage.INSTANCE,
                  filesUsed,
                ),
                SafePrompt.Message.TextChunk("The selected text is:", filesUsed),
                SafePrompt.Message.CodeChunk(
                  contents.subSequence(selectionStart, selectionEnd).toString(),
                  PlainTextLanguage.INSTANCE,
                  filesUsed,
                ),
                SafePrompt.Message.TextChunk(
                  "The contents after the selected text are:",
                  filesUsed,
                ),
                SafePrompt.Message.CodeChunk(
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
  fun buildPrompt_openFileContents_noSelection() {
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
        SafePromptImpl(
          listOf(
            SafePrompt.UserMessage(
              listOf(
                SafePrompt.Message.TextChunk("The file \"/src/$path\" is open.", filesUsed),
                SafePrompt.Message.TextChunk("The contents before the caret are:", filesUsed),
                SafePrompt.Message.CodeChunk(
                  contents.take(caretOffset),
                  PlainTextLanguage.INSTANCE,
                  filesUsed,
                ),
                SafePrompt.Message.TextChunk("The contents after the caret are:", filesUsed),
                SafePrompt.Message.CodeChunk(
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

  @Test
  fun buildPrompt_noMessagesThrowsError() {
    assertFailsWith<MalformedPromptException> { buildPrompt(project) {} }
  }

  @Test
  fun buildPrompt_wrongLastMessageThrowsError() {
    assertFailsWith<MalformedPromptException> {
      buildPrompt(project) {
        systemMessage { text("preamble", emptyList()) }
        userMessage { text("user", emptyList()) }
        // The last message should be a user message
        modelMessage { text("model", emptyList()) }
      }
    }
  }

  @Test
  fun buildPrompt_wrongSystemMessageThrowsError() {
    assertFailsWith<MalformedPromptException> {
      buildPrompt(project) {
        userMessage { text("user", emptyList()) }
        modelMessage { text("model", emptyList()) }
        // System message should be the first message
        systemMessage { text("preamble", emptyList()) }
        userMessage { text("user", emptyList()) }
      }
    }
  }

  @Test
  fun buildPrompt_twoSystemMessagesThrowsError() {
    assertFailsWith<MalformedPromptException> {
      buildPrompt(project) {
        // Only one system message is allowed
        systemMessage { text("preamble", emptyList()) }
        systemMessage { text("preamble 2", emptyList()) }
        userMessage { text("user", emptyList()) }
        modelMessage { text("model", emptyList()) }
        userMessage { text("user", emptyList()) }
      }
    }
  }

  @Test
  fun buildPrompt_enforcesAiExclude() {
    myFixture.addFileToProject("/.aiexclude", "*.txt")
    val file = myFixture.addFileToProject("/file.txt", "").virtualFile

    buildPrompt(project) {
      systemMessage { text("preamble", emptyList()) }
      userMessage { text("user", emptyList()) }
      modelMessage { text("model", emptyList()) }
      userMessage { text("user", listOf(file)) }
    }

    whenever(mockAiExcludeService.isFileExcluded(project, file)).thenReturn(true)
    assertFailsWith<AiExcludeException> {
      buildPrompt(project) {
        systemMessage { text("preamble", emptyList()) }
        userMessage { text("user", emptyList()) }
        modelMessage { text("model", emptyList()) }
        userMessage { text("user", listOf(file)) }
      }
    }

    assertFailsWith<AiExcludeException> {
      buildPrompt(project) {
        systemMessage { text("preamble", emptyList()) }
        userMessage { text("user", emptyList()) }
        modelMessage { text("model", listOf(file)) }
        userMessage { text("user", emptyList()) }
      }
    }
  }
}
