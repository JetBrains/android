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
package com.android.tools.idea.compose.preview.actions.ml.utils

import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.studiobot.GenerationConfig
import com.android.tools.idea.studiobot.MimeType
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.Prompt
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * Given a [Prompt] with instructions to modify a given file, this method queries a model and
 * applies the given [callback] with the generated code and original [PsiFile].
 */
internal suspend fun generateCodeAndExecuteCallback(
  prompt: Prompt,
  filePointer: SmartPsiElementPointer<PsiFile>,
  progressIndicatorText: String = message("ml.actions.progress.indicator.sending.query"),
  callback: (Project, PsiFile, KotlinCodeBlock) -> Unit = ::mergeBlockAndShowDiff,
) {
  val project = filePointer.project
  val studioBot = StudioBot.getInstance()

  withContext(AndroidDispatchers.workerThread) {
    // Send the prompt + code directly to the model, with a progress indicator
    withBackgroundProgress(project, progressIndicatorText, true) {
      val botResponse =
        studioBot
          .model(project)
          .generateCode(
            legacyClientSidePrompt = prompt,
            language = MimeType.KOTLIN,
            config = GenerationConfig(candidateCount = 1),
            userQuery = "",
            fileContext = null,
            // TODO(b/356347457): Properly migrate to new `generateCode` API
            history = prompt,
          )
          .first()
          .text

      withContext(AndroidDispatchers.uiThread) uiThread@{
        val psiFile = filePointer.element ?: return@uiThread
        val parsedBlock = generateKotlinCodeBlock(project, psiFile, botResponse)
        callback(project, psiFile, parsedBlock)
      }
    }
  }
}

/**
 * Modifies a [Document] given a [kotlinCodeBlock] and shows a diff view to compare the resulting
 * document with a given [psiFile].
 */
private fun mergeBlockAndShowDiff(
  project: Project,
  psiFile: PsiFile,
  kotlinCodeBlock: KotlinCodeBlock,
) {
  val modifiedDocument: Document = mergeOrAppendResponse(psiFile, kotlinCodeBlock)

  val diffFactory = DiffContentFactory.getInstance()
  val originalContent = diffFactory.create(project, psiFile.virtualFile)
  val modifiedContent = diffFactory.create(project, modifiedDocument, psiFile.fileType)
  val request =
    SimpleDiffRequest(
      "Review Code Changes",
      originalContent,
      modifiedContent,
      "${psiFile.name} (Original)",
      "${psiFile.name} (Proposed)",
    )

  // wrapping the request in a chain to make sure user data is taken into account
  val requestChain = SimpleDiffRequestChain(request)
  requestChain.putUserData(DiffUserDataKeysEx.DIFF_NEW_TOOLBAR, true)
  requestChain.putUserData(DiffUserDataKeysEx.DISABLE_CONTENTS_EQUALS_NOTIFICATION, true)

  // TODO: Add "Accept all changes" action. Maybe triggering action.Diff.ApplyNonConflicts
  service<DiffManager>().showDiff(project, requestChain, DiffDialogHints.DEFAULT)
}

/**
 * Merges [parsedBlock] into [psiFile] by matching function names. If the parsedBlock can´t be
 * merged, appends it to the end of the file.
 */
private fun mergeOrAppendResponse(psiFile: PsiFile, parsedBlock: KotlinCodeBlock): Document {
  val project = psiFile.project
  val codeMerger = CodeMerger(project)
  val merged = codeMerger.mergeBlock(parsedBlock, psiFile, KotlinLanguage.INSTANCE)
  if (merged != null) {
    return merged
  }
  return appendBlock(project, psiFile, parsedBlock)
}

private fun generateKotlinCodeBlock(
  project: Project,
  psiFile: PsiFile,
  botResponse: String,
): KotlinCodeBlock {
  val codeParser = KotlinParser(project, psiFile)
  // TODO: handle errors
  val parsedBlock = codeParser.parse(botResponse.formatResponse())
  return parsedBlock
}

/**
 * Formats a code response from a model and returns the formatted string.
 *
 * Supported formats:
 * * Code
 * * Code wrapped into a Kotlin code markdown block, i.e. ```kotlin <Code>```
 * * Code wrapped into a generic code markdown block, i.e. ```<Code>```
 */
private fun String.formatResponse(): String {
  // First, remove all trailing/leading whitespaces or blank lines
  val trimmedString = this.trim()
  val codeMarkdownTag = "```"
  if (startsWith(codeMarkdownTag)) {
    val kotlinCodeMarkdownTag = "```kotlin"
    // Start after ```kotlin if the Kotlin tag is present, or after ``` otherwise
    val startIndex =
      if (startsWith(kotlinCodeMarkdownTag)) kotlinCodeMarkdownTag.length
      else codeMarkdownTag.length
    return trimmedString.substring(startIndex).substringBeforeLast("```")
  } else {
    // There is no markdown formatting. Return the code directly.
    return trimmedString
  }
}
