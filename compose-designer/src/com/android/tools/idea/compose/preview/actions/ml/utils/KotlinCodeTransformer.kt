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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.studiobot.GenerationConfig
import com.android.tools.idea.studiobot.ModelType
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.Prompt
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * Given the code of a Compose Preview function, an optional [Blob] representing its render and a
 * containing file, transforms the code according to a given query and show a diff view so the user
 * can compare the changes.
 */
internal class KotlinCodeTransformer {
  fun transformAndShowDiff(
    query: String,
    previewFunctionCode: String,
    filePointer: SmartPsiElementPointer<PsiFile>,
    blob: Blob?,
    disposable: Disposable,
  ): Job {
    val project = filePointer.project
    val studioBot = StudioBot.getInstance()

    // Send the prompt + code directly to the model, with a progress indicator
    return AndroidCoroutineScope(disposable).launch(AndroidDispatchers.workerThread) {
      withBackgroundProgress(project, message("circle.to.fix.sending.query"), true) {
        val botResponse =
          studioBot
            // TODO: upgrade to gemini
            .model(project, ModelType.EXPERIMENTAL_VISION)
            .generateContent(
              buildPrompt(filePointer, previewFunctionCode, blob, query),
              GenerationConfig(candidateCount = 1),
            )
            .first()
            .text

        withContext(AndroidDispatchers.uiThread) {
          val psiFile = filePointer.element ?: return@withContext
          val parsedBlock = generateKotlinCodeBlock(project, psiFile, botResponse)

          val modifiedDocument: Document = mergeResponse(psiFile, parsedBlock)
          showDiff(project, psiFile, modifiedDocument.text)
        }
      }
    }
  }

  private fun showDiff(project: Project, psiFile: PsiFile, modifiedDocument: String) {
    val diffFactory = DiffContentFactory.getInstance()
    val originalContent = diffFactory.create(project, psiFile.virtualFile)
    val modifiedContent = diffFactory.create(project, modifiedDocument)
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
   * Merges [parsedBlock] into [psiFile] by matching function names. Returns the original document
   * if the parsedBlock can´t be merged.
   */
  private fun mergeResponse(psiFile: PsiFile, parsedBlock: KotlinCodeBlock): Document {
    val project = psiFile.project
    val merged = CodeMerger(project).mergeBlock(parsedBlock, psiFile, KotlinLanguage.INSTANCE)
    if (merged != null) {
      return merged
    }
    return PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
  }
}

/**
 * Takes the code of a Compose Preview function, its corresponding image, and a custom instruction.
 * Builds a prompt asking for the transformed function code.
 */
private fun buildPrompt(
  filePointer: SmartPsiElementPointer<PsiFile>,
  selectedCode: String,
  blob: Blob?,
  query: String,
): Prompt {
  val defaultPrompt =
    """
      |You are an expert Android programmer knowledgeable in Kotlin and Java.
      |You follow all the best coding practices.
      """
      .trimMargin()
  return buildPrompt(filePointer.project) {
    userMessage {
      // TODO: This was supposed to be a systemMessage, but the current model (Vision) doesn't
      //  support multi-turn
      text(defaultPrompt, listOf())
      text("This is the code corresponding to the following Compose Preview:", listOf())
      filePointer.element?.let {
        code(selectedCode, KotlinLanguage.INSTANCE, listOf(it.virtualFile))
      }
      blob?.let { blob(data = it.data, mimeType = it.mimeType, filesUsed = emptyList()) }
      text(
        "The response must modify the code above in order to achieve the following: $query",
        listOf(),
      )
      text(
        "The response must contain the entire function code, not only the modified parts.",
        listOf(),
      )
      text(
        "The response must only include the modified code, not any other piece of text.",
        listOf(),
      )
    }
  }
}

private fun generateKotlinCodeBlock(
  project: Project,
  psiFile: PsiFile,
  botResponse: String,
): KotlinCodeBlock {
  val codeParser = KotlinParser(project, psiFile)
  // TODO: handle errors
  val parsedBlock = codeParser.parse(botResponse)
  return parsedBlock
}
