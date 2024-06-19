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
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.VALUE_VERTICAL
import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlTag

class InefficientWeightQuickFix(element: PsiElement) :
  PsiBasedModCommandAction<PsiElement>(element) {

  override fun getFamilyName() = "InefficientWeightQuickFix"

  override fun getPresentation(context: ActionContext, element: PsiElement) =
    PsiTreeUtil.getParentOfType(element, XmlTag::class.java)?.parentTag?.let {
      Presentation.of(message("android.lint.fix.replace.with.zero.dp"))
    }

  override fun perform(context: ActionContext, element: PsiElement): ModCommand {
    val tag =
      element.parents(false).filterIsInstance(XmlTag::class.java).firstOrNull()
        ?: return ModCommand.nop()
    val parentTag = tag.parentTag ?: return ModCommand.nop()

    val attrName =
      if (VALUE_VERTICAL == parentTag.getAttributeValue(ATTR_ORIENTATION, ANDROID_URI)) {
        ATTR_LAYOUT_HEIGHT
      } else {
        ATTR_LAYOUT_WIDTH
      }

    @Suppress("UnstableApiUsage")
    return ModCommand.psiUpdate(tag) { tagCopy, _ ->
      tagCopy.setAttribute(attrName, ANDROID_URI, "0dp")
    }
  }
}
