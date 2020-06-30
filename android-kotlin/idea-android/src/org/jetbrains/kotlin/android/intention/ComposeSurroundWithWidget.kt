/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.kotlin.android.intention

import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.impl.InvokeTemplateAction
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.android.compose.isInsideComposableCode
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinStatementSurroundDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.util.HashSet

/**
 * Surrounds selected statements inside composable function with widget.
 *
 * @see intentionDescriptions/ComposeSurroundWithWidget/before.kt.template
 *      intentionDescriptions/ComposeSurroundWithWidget/after.kt.template
 */
class ComposeSurroundWithWidget : IntentionAction, HighPriorityAction {
  override fun getText(): String = AndroidBundle.message("compose.surround.with.widget.intention.text")

  override fun getFamilyName() = "Compose Surround With Widget"

  override fun startInWriteAction(): Boolean = true

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    when {
      !StudioFlags.COMPOSE_EDITOR_SUPPORT.get() -> return false
      file == null || editor == null -> return false
      !file.isWritable || file !is KtFile || !editor.selectionModel.hasSelection() -> return false
      else -> {
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        if (!element.isInsideComposableCode()) return false

        val statements = KotlinStatementSurroundDescriptor()
          .getElementsToSurround(file, editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)

        return statements.isNotEmpty()
      }
    }
  }

  private fun getTemplate(): TemplateImpl? {
    return TemplateSettings.getInstance().getTemplate("W", "AndroidCompose")
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    InvokeTemplateAction(getTemplate(), editor, project, HashSet()).perform()
  }

}
