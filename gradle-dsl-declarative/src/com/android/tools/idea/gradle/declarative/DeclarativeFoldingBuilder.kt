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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlock
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFile
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class DeclarativeFoldingBuilder : CustomFoldingBuilder(), DumbAware {
  override fun buildLanguageFoldRegions(
    descriptors: MutableList<FoldingDescriptor>,
    root: PsiElement, document: Document, quick: Boolean
  ) {
    if (root !is DeclarativeFile) {
      return
    }
    appendDescriptors(root.node, document, descriptors)
  }

  private fun appendDescriptors(node: ASTNode, document: Document, descriptors: MutableList<FoldingDescriptor>) {
    if (needFolding(node)) {
      val textRange = getRangeToFold(node, document)
      descriptors.add(FoldingDescriptor(node, textRange))
    }

    var child = node.firstChildNode
    while (child != null) {
      appendDescriptors(child, document, descriptors)
      child = child.treeNext
    }
  }

  private fun getRangeToFold(node: ASTNode, document: Document): TextRange {
    val psi = node.psi
    if (psi is DeclarativeBlock) {
     psi.identifier?.nextSibling?.let {
        val start = it.textRange.startOffset
        val end = psi.textRange.endOffset
        return TextRange(start,end)
      }
    }

    return node.textRange
  }

  private fun needFolding(node: ASTNode): Boolean {
    val type = node.elementType

    return type == DeclarativeElementTypeHolder.BLOCK_COMMENT ||
           type == DeclarativeElementTypeHolder.BLOCK
  }

  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String = when (node.elementType) {
    DeclarativeElementTypeHolder.BLOCK_COMMENT -> "/* ... */"
    DeclarativeElementTypeHolder.BLOCK -> "{...}"
    else -> "{...}"
  }

  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean = false
}