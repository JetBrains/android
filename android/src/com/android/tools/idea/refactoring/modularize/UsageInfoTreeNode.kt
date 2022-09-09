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
import com.intellij.openapi.util.Iconable.ICON_FLAG_READ_STATUS
import com.intellij.openapi.util.Iconable.ICON_FLAG_VISIBILITY
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import javax.swing.Icon

class UsageInfoTreeNode(usageInfo: UsageInfo, referenceCount: Int) : DependencyTreeNode(usageInfo, referenceCount) {
  val psiElement = usageInfo.element

  override fun render(renderer: ColoredTreeCellRenderer) {
    renderer.icon = ApplicationManager.getApplication().runReadAction<Icon> {
      psiElement!!.getIcon(ICON_FLAG_VISIBILITY or ICON_FLAG_READ_STATUS)
    }

    when (psiElement) {
      is PsiFile -> {
        // Currently, we only move entire code files
        renderer.append(psiElement.name, textAttributes)
        renderQualifiers(getFolderConfiguration(psiElement), renderer, textAttributes)
        renderReferenceCount(renderer, textAttributes)
      }
      is PsiClass -> {
        // Unused
        renderer.append(psiElement.name ?: "<unknown>", textAttributes)
        renderReferenceCount(renderer, textAttributes)
      }
      is KtClass -> {
        // Unused
        renderer.append(psiElement.name ?: "<unknown>", textAttributes)
        renderReferenceCount(renderer, textAttributes)
      }
      is KtDeclaration -> {
        // Unused
        renderer.append(psiElement.name ?: "<unknown>", textAttributes)
        renderReferenceCount(renderer, textAttributes)
      }
      is XmlTag -> {
        // TODO: use a syntax highlighter? SyntaxHighlighterFactory.getSyntaxHighlighter(psiElement.getLanguage(), null, null)
        renderer.append(psiElement.text, textAttributes)
      }
      else -> throw IllegalArgumentException("Unknown psiElement $psiElement")
    }
  }

  private fun renderQualifiers(config: FolderConfiguration?, renderer: ColoredTreeCellRenderer, attr: SimpleTextAttributes) {
    val qualifier = config!!.qualifierString
    if(qualifier.isNotBlank()) {
      renderer.append(" ($qualifier)", SimpleTextAttributes(attr.style or STYLE_SMALLER, attr.fgColor))
    }
  }
}