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
import com.android.tools.compose.isValidPreviewLocation
import com.android.tools.idea.compose.preview.actions.ml.utils.transformAndShowDiff
import com.android.tools.idea.compose.preview.isMultiPreviewAnnotation
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.studiobot.MimeType
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

/**
 * Base class for actions that aim to generate Compose Previews for target @Composable functions.
 * Each subclass must override [getTargetComposableFunctions] to define the targets.
 */
abstract class GenerateComposePreviewBaseAction(text: String) : AnAction(text) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl ?: return
    val filePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
    val composableFunctions = getTargetComposableFunctions(e)
    if (composableFunctions.isEmpty()) return
    val prompt = buildPrompt(filePointer, composableFunctions)
    transformAndShowDiff(prompt, filePointer, editor.disposable)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = false
    if (!isActionFlagEnabled()) return
    val project = e.project ?: return
    val studioBot = StudioBot.getInstance()
    if (!studioBot.isContextAllowed(project)) return
    if (getTargetComposableFunctions(e).isEmpty()) return

    // Only enable the action if there are target Composable functions available
    e.presentation.isEnabledAndVisible = true
  }

  /**
   * Whether this [KtNamedFunction] is a valid Composable function, i.e. a function annotated
   * with @Composable that's not yet annotated with a @Preview or a MultiPreview annotation. It also
   * must be in a valid preview location (see [isValidPreviewLocation]).
   */
  protected fun KtNamedFunction.isValidComposableFunction(): Boolean {
    if (annotationEntries.none { it.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME) }) return false
    if (!isValidPreviewLocation()) return false
    if (
      annotations.any {
        val uAnnotation = (it.toUElement() as? UAnnotation) ?: return@any false
        return@any uAnnotation.isPreviewAnnotation() || uAnnotation.isMultiPreviewAnnotation()
      }
    )
      return false
    return true
  }

  protected abstract fun getTargetComposableFunctions(e: AnActionEvent): List<KtNamedFunction>

  protected abstract fun isActionFlagEnabled(): Boolean

  /**
   * Builds a [Prompt] that uses [PREAMBLE] as an explanation of how to create Compose Previews
   * given a Composable. Then, the prompt asks the model to create a Compose Preview for each given
   * function in [composableFunctions]. The list is expected not to be empty.
   */
  private fun buildPrompt(
    filePointer: SmartPsiElementPointer<PsiFile>,
    composableFunctions: List<KtNamedFunction>,
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
          "Generate the code for a Compose Preview corresponding to the following Composable(s):",
          listOf(),
        )
        filePointer.element?.let {
          code(
            composableFunctions.joinToString(
              transform = { function -> function.text },
              separator = "\n\n",
            ),
            MimeType.KOTLIN,
            listOf(it.virtualFile),
          )
        }
        text(
          """
          |The response must contain the same number of functions as the number provided as input.
          |The response must contain the entire function(s) code, not only the modified parts.
          |The response must only include the modified or new code, not any other piece of text.
          """
            .trimMargin(),
          listOf(),
        )
      }
    }
  }
}
