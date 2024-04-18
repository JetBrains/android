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
package com.android.tools.idea.compose.preview.actions.ml

import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.idea.compose.preview.actions.ml.utils.transformAndShowDiff
import com.android.tools.idea.compose.preview.isMultiPreviewAnnotation
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.Prompt
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

private const val PREAMBLE =
  """
@Preview annotation can't be added directly to Composables that have parameters.

For example, if you have a `@Composable` that takes as input user information like this:

```
@Composable
fun UserProfile(user: User) {
    Column {
      Text("Name: ${'$'}{user.name}")
      Text("Age: ${'$'}{user.age}")
    }
}
```

You can't add a @Preview annotation directly to the `UserProfile` function. Instead, you can can
create a separate function and call the `UserProfile` function with the necessary arguments.

For the example above, the code for a generated Compose Preview would look like this:

```
@Preview
@Composable
fun UserProfilePreview() {
  val user = User(name = "John", surname = "Doe", age = 20)
  UserProfile(user)
}
```

"""

/** Action to generate a Compose Preview for a target Composable function. */
class GenerateComposePreviewAction : AnAction(message("action.generate.preview")) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val containingFunction = getContainingFunctionAtCaret(e) ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl ?: return
    val filePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
    val prompt = buildPrompt(filePointer, containingFunction.text)
    transformAndShowDiff(prompt, filePointer, editor.disposable)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = false
    if (!StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW.get()) return
    val project = e.project ?: return
    val studioBot = StudioBot.getInstance()
    if (!studioBot.isContextAllowed(project)) return

    // Only enable the action for Composables not yet annotated with @Preview or multipreview
    val containingFunction = getContainingFunctionAtCaret(e) ?: return
    val annotations = containingFunction.annotationEntries
    if (annotations.none { it.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME) }) return
    if (
      annotations.any {
        val uAnnotation = (it.toUElement() as? UAnnotation) ?: return@any false
        return@any uAnnotation.isPreviewAnnotation() || uAnnotation.isMultiPreviewAnnotation()
      }
    )
      return

    e.presentation.isEnabledAndVisible = true
  }

  private fun getContainingFunctionAtCaret(e: AnActionEvent): KtNamedFunction? {
    val caret = e.getData(CommonDataKeys.CARET) ?: return null
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return null
    val psiElement = runReadAction { psiFile.findElementAt(caret.offset) } ?: return null
    return psiElement.parentOfType<KtNamedFunction>()
  }

  /**
   * Builds a [Prompt] that uses [PREAMBLE] as an explanation of how to create Compose Previews
   * given a Composable, and the given [composableFunctionCode] as the target code.
   */
  private fun buildPrompt(
    filePointer: SmartPsiElementPointer<PsiFile>,
    composableFunctionCode: String,
  ): Prompt {
    val defaultPrompt =
      """
      |You are an expert Android programmer knowledgeable in Kotlin and Java.
      |You follow all the best coding practices.
      """
        .trimMargin()
    return com.android.tools.idea.studiobot.prompts.buildPrompt(filePointer.project) {
      systemMessage {
        text(defaultPrompt, listOf())
        text(PREAMBLE, listOf())
      }
      userMessage {
        text(
          "Generate the code for a Compose Preview corresponding to the following Composable:",
          listOf(),
        )
        filePointer.element?.let {
          code(composableFunctionCode, KotlinLanguage.INSTANCE, listOf(it.virtualFile))
        }
        text(
          """
          |The response must contain the entire function code, not only the modified parts.
          |The response must only include the modified code, not any other piece of text.
          """
            .trimMargin(),
          listOf(),
        )
      }
    }
  }
}
