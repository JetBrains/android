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

import com.android.SdkConstants
import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

class SetScrollViewSizeQuickFix(element: PsiElement) :
  PsiBasedModCommandAction<PsiElement>(element) {
  override fun getFamilyName() = "SetScrollViewSizeQuickFix"

  override fun getPresentation(context: ActionContext, element: PsiElement) =
    PsiTreeUtil.getParentOfType(element, XmlTag::class.java)?.parentTag?.let {
      Presentation.of(message("android.lint.fix.set.to.wrap.content"))
    }

  override fun perform(context: ActionContext, element: PsiElement): ModCommand {
    val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java) ?: return ModCommand.nop()
    val parentTag = tag.parentTag ?: return ModCommand.nop()

    @Suppress("UnstableApiUsage")
    return ModCommand.psiUpdate(tag) { tagCopy, _ ->
      val isHorizontal = SdkConstants.HORIZONTAL_SCROLL_VIEW == parentTag.name
      val attributeName =
        if (isHorizontal) SdkConstants.ATTR_LAYOUT_WIDTH else SdkConstants.ATTR_LAYOUT_HEIGHT
      tagCopy.setAttribute(attributeName, SdkConstants.ANDROID_URI, SdkConstants.VALUE_WRAP_CONTENT)
    }
  }
}
