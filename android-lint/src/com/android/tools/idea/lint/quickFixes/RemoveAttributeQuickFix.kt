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
package com.android.tools.idea.lint.quickFixes

import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute

class RemoveAttributeQuickFix(element: PsiElement) : PsiBasedModCommandAction<PsiElement>(element) {
  override fun getFamilyName() = "RemoveAttributeQuickFix"

  override fun getPresentation(context: ActionContext, element: PsiElement) =
    PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java)?.let {
      Presentation.of(message("android.lint.fix.remove.attribute"))
    }

  override fun perform(context: ActionContext, element: PsiElement): ModCommand {
    val attribute =
      PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java) ?: return ModCommand.nop()

    @Suppress("UnstableApiUsage")
    return ModCommand.psiUpdate(attribute) { attr, _ -> attr.parent?.setAttribute(attr.name, null) }
  }
}
