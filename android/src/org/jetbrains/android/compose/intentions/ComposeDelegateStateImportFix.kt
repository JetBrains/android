/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.compose.intentions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.js.translate.callTranslator.getReturnType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * Registers ComposeDelegateStateImportFixFactory for DELEGATE_SPECIAL_FUNCTION_MISSING error.
 *
 * DELEGATE_SPECIAL_FUNCTION_MISSING is an error when there is no getValue or setValue function for an object after "by" keyword.
 *
 * TODO(b/157543181):Delete code after JB bug is fixed.
 */
class ComposeDelegateStateImportFixContributor : QuickFixContributor {
  override fun registerQuickFixes(quickFixes: QuickFixes) {
    quickFixes.register(Errors.DELEGATE_SPECIAL_FUNCTION_MISSING, ComposeDelegateStateImportFixFactory())
  }
}

/**
 * Creates an IntentionAction that allow to add [androidx.compose.runtime.getValue] import for [androidx.compose.runtime.MutableState] in delegate position.
 */
private class ComposeDelegateStateImportFixFactory : KotlinSingleIntentionActionFactory() {
  override fun createAction(diagnostic: Diagnostic): IntentionAction? {
    val callExpression: KtCallExpression = diagnostic.psiElement as? KtCallExpression ?: return null
    val delegateFqName = callExpression.getResolvedCall(callExpression.analyze(BodyResolveMode.FULL))?.getReturnType()?.fqName
    if (delegateFqName?.asString() == "androidx.compose.runtime.MutableState") {
      return ComposeDelegateStateImportFixAction()
    }
    return null
  }
}

private class ComposeDelegateStateImportFixAction : IntentionAction {
  override fun startInWriteAction() = false

  override fun getFamilyName() = text

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun getText() = AndroidBundle.message("import.get.value")

  // Inspired by KotlinAddImportAction#addImport
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null || file !is KtFile) return
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitAllDocuments()

    project.executeWriteCommand(QuickFixBundle.message("add.import")) {
      val descriptor = file.resolveImportReference(FqName("androidx.compose.runtime.getValue")).firstOrNull() ?: return@executeWriteCommand
      ImportInsertHelper.getInstance(project).importDescriptor(file, descriptor)
    }
  }
}
