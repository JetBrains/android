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
package com.android.tools.idea.lang.androidSql.refactoring

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlBindParameter
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlNameElement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes
import com.intellij.lang.ASTFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType


/**
 * [AbstractElementManipulator] that inserts the new name (possibly quoted) into the PSI.
 *
 * It also specifies the range in element, which for quoted names does not include the quotes. This fixes the inline renamer,
 * because SQL remains valid when renaming quoted names.
 */
class AndroidSqlNameElementManipulator : AbstractElementManipulator<AndroidSqlNameElement>() {
  override fun handleContentChange(element: AndroidSqlNameElement, range: TextRange, newContent: String): AndroidSqlNameElement {
    element.node.replaceChild(
      element.node.firstChildNode,
      if (AndroidSqlLexer.needsQuoting(newContent)) {
        newLeaf(AndroidSqlPsiTypes.BACKTICK_LITERAL, AndroidSqlLexer.getValidName(newContent))
      } else {
        newLeaf(AndroidSqlPsiTypes.IDENTIFIER, newContent)
      }
    )
    return element
  }

  override fun getRangeInElement(nameElement: AndroidSqlNameElement): TextRange =
    if (nameElement.nameIsQuoted) TextRange(1, nameElement.textLength - 1) else TextRange(0, nameElement.textLength)
}

class AndroidSqlBindParameterManipulator : AbstractElementManipulator<AndroidSqlBindParameter>() {
  override fun handleContentChange(element: AndroidSqlBindParameter, range: TextRange, newContent: String): AndroidSqlBindParameter {
    element.node.replaceChild(element.node.firstChildNode, newLeaf(AndroidSqlPsiTypes.BIND_PARAMETER, ":$newContent"))
    return element
  }

  override fun getRangeInElement(element: AndroidSqlBindParameter): TextRange = TextRange(1, element.textLength)
}

/**
 * Creates a new leaf to be inserted in the tree.
 *
 * The node needs to be marked as generated, otherwise refactoring code doesn't know how to format the new code.
 *
 * @see com.intellij.psi.impl.source.PostprocessReformattingAspect
 */
private fun newLeaf(type: IElementType, text: String): LeafElement {
  return ASTFactory.leaf(type, text).also { CodeEditUtil.setNodeGenerated(it, true) }
}
