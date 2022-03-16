/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.idea.res.getFolderConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usageView.UsageInfo
import javax.swing.Icon

class UsageInfoTreeNode(usageInfo: UsageInfo, referenceCount: Int) : DependencyTreeNode(usageInfo, referenceCount) {
  val psiElement = usageInfo.element

  override fun render(renderer: ColoredTreeCellRenderer) {
    renderer.icon = ApplicationManager.getApplication().runReadAction<Icon> {
      psiElement!!.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)
    }

    val inheritedAttributes = textAttributes
    if (psiElement is PsiFile) {
      renderer.append(psiElement.name, inheritedAttributes)
      renderQualifiers(getFolderConfiguration(psiElement), renderer, inheritedAttributes)
      renderReferenceCount(renderer, inheritedAttributes)
    } else if (psiElement is PsiClass) {
      val psiClass: PsiClass = psiElement
      renderer.append(if (psiClass.name == null) "<unknown>" else psiClass.name!!, inheritedAttributes)
      renderReferenceCount(renderer, inheritedAttributes)
    } else if (psiElement is XmlTag) {
      // TODO: use a syntax highlighter? SyntaxHighlighterFactory.getSyntaxHighlighter(psiElement.getLanguage(), null, null)
      renderer.append(psiElement.text, inheritedAttributes)
    } else {
      throw IllegalArgumentException("Unknown psiElement $psiElement")
    }
  }

  companion object {
    private fun renderQualifiers(
      folderConfig: FolderConfiguration?,
      renderer: ColoredTreeCellRenderer,
      inheritedAttributes: SimpleTextAttributes,
    ) {
      val config = folderConfig!!.qualifierString
      if (!StringUtil.isEmptyOrSpaces(config)) {
        val derivedAttributes = SimpleTextAttributes(
          inheritedAttributes.style or SimpleTextAttributes.STYLE_SMALLER,
          inheritedAttributes.fgColor,
        )
        renderer.append(" ($config)", derivedAttributes)
      }
    }
  }
}