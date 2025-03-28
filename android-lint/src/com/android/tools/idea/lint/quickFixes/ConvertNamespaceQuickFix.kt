/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.SdkConstants.XMLNS
import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

class ConvertNamespaceQuickFix(element: PsiElement) :
  PsiBasedModCommandAction<PsiElement>(element) {
  override fun getFamilyName() = "ConvertNamespaceQuickFix"

  override fun getPresentation(context: ActionContext, element: PsiElement) =
    PsiTreeUtil.getParentOfType(element, XmlTag::class.java)?.let {
      Presentation.of(message("android.lint.fix.replace.namespace"))
    }

  override fun perform(context: ActionContext, element: PsiElement): ModCommand {
    val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java) ?: return ModCommand.nop()

    @Suppress("UnstableApiUsage")
    return ModCommand.psiUpdate(tag) { tagCopy, _ ->
      for (attribute in tagCopy.attributes) {
        if (attribute.name.startsWith(XMLNS)) {
          val uri = attribute.value
          if (!uri.isNullOrEmpty() && uri.startsWith(URI_PREFIX) && uri != ANDROID_URI) {
            attribute.setValue(AUTO_URI)
          }
        }
      }
    }
  }
}
