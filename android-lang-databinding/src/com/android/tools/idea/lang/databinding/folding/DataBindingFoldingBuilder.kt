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
package com.android.tools.idea.lang.databinding.folding

import com.android.utils.isBindingExpression
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken


class DataBindingFoldingBuilder : FoldingBuilderEx() {

  private val escapeFolds = mapOf(
    "&lt;" to "<",
    "&gt;" to ">",
    "&amp;" to "&",
    "&quot;" to "\"",
    "&apos;" to "'"
  )

  override fun getPlaceholderText(node: ASTNode): String? {
    return escapeFolds[node.text]
  }

  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
    val descriptors = ArrayList<FoldingDescriptor>()

    PsiTreeUtil.findChildrenOfType(root, XmlToken::class.java)
      .filter { escapeFolds.containsKey(it.text) && (isDbExpression(it) || isVariableType(it)) }
      .forEach {
        descriptors.add(object : FoldingDescriptor(it.node, it.textRange, null) {
          override fun getPlaceholderText(): String? {
            return escapeFolds[it.text]
          }
        })
      }
    return descriptors.toTypedArray()
  }

  override fun isCollapsedByDefault(node: ASTNode): Boolean {
    return true
  }


  /**
   * @return true if the xmlToken is inside data binding expression.
   */
  private fun isDbExpression(xmlToken: XmlToken): Boolean {
    val xmlAttributeValue = xmlToken.parent as? XmlAttributeValue ?: return false
    return isBindingExpression(xmlAttributeValue.value)
  }

  /**
   * @return true if the xmlToken is inside data binding variable type.
   */
  private fun isVariableType(xmlToken: XmlToken): Boolean {
    val xmlAttributeValue = xmlToken.parent as? XmlAttributeValue ?: return false
    val typeAttribute = xmlAttributeValue.parent as? XmlAttribute ?: return false
    val variableTag = typeAttribute.parent
    val dataTag = variableTag.parent as? XmlTag ?: return false
    return dataTag.name == "data" && variableTag.name == "variable" && typeAttribute.name == "type"
  }
}