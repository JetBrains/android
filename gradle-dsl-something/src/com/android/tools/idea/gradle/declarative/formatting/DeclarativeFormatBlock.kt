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
package com.android.tools.idea.gradle.declarative.formatting

import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.ASSIGNMENT
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BLOCK
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BLOCK_GROUP
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.FACTORY
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.LINE_COMMENT
import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil.isEmpty
import com.intellij.psi.formatter.FormatterUtil
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class DeclarativeFormatBlock(
  private val node: ASTNode,
  private val indent: Indent?,
  private val ctx: DeclarativeFormatContext
) : ASTBlock {
  override fun getNode(): ASTNode = node
  override fun getTextRange(): TextRange = node.textRange
  override fun getAlignment(): Alignment? = null
  override fun getIndent(): Indent? = indent
  override fun getWrap(): Wrap? = null

  override fun getSubBlocks(): List<Block> = mySubBlocks
  private val mySubBlocks: List<Block> by lazy { buildChildren() }

  private fun buildChildren(): List<Block> {
    return node.getChildren(null)
      .filter { !isEmpty(it.text.trim()) }
      .map { childNode: ASTNode ->
        DeclarativeFormatBlock(
          node = childNode,
          indent = computeIndent(childNode),
          ctx = ctx
        )
      }
  }

  override fun getSpacing(child1: Block?, child2: Block): Spacing? =
    ctx.spacingBuilder.getSpacing(this, child1, child2)

  override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
    return if (TokenSet.create(BLOCK_GROUP).contains(node.getElementType())) {
      ChildAttributes(Indent.getNormalIndent(), null)
    }
    else ChildAttributes(Indent.getNoneIndent(), null)
  }

  override fun isLeaf(): Boolean = node.firstChildNode == null

  override fun isIncomplete(): Boolean = myIsIncomplete
  private val myIsIncomplete: Boolean by lazy { FormatterUtil.isIncomplete(node) }

  override fun toString() = "${node.text} $textRange"

  private fun computeIndent(child: ASTNode): Indent {
    return when (node.elementType) {
      is IFileElementType -> Indent.getNoneIndent()
      BLOCK_GROUP -> when (child.elementType) {
        ASSIGNMENT, FACTORY, BLOCK, LINE_COMMENT -> Indent.getNormalIndent()
        else -> Indent.getNoneIndent()
      }
      else -> Indent.getNoneIndent()
    }
  }
}
