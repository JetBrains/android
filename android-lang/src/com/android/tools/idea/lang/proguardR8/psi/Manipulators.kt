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
package com.android.tools.idea.lang.proguardR8.psi

import com.android.tools.idea.lang.proguardR8.parser.ProguardR8Lexer
import com.android.tools.idea.lang.proguardR8.parser.ProguardR8Parser
import com.android.tools.idea.lang.proguardR8.parser.ProguardR8ParserDefinition
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.DOUBLE_QUOTED_CLASS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.SINGLE_QUOTED_CLASS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.UNTERMINATED_DOUBLE_QUOTED_CLASS
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.UNTERMINATED_SINGLE_QUOTED_CLASS
import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.IncorrectOperationException


class ProguardR8QualifiedNameManipulator : AbstractElementManipulator<ProguardR8QualifiedName>() {

  private val quotedElements = arrayOf(
    SINGLE_QUOTED_CLASS,
    DOUBLE_QUOTED_CLASS,
    UNTERMINATED_SINGLE_QUOTED_CLASS,
    UNTERMINATED_DOUBLE_QUOTED_CLASS
  )
  private val terminatedQuotedElements = arrayOf(SINGLE_QUOTED_CLASS, DOUBLE_QUOTED_CLASS)

  private fun createQualifiedNameFromText(text: String, element: ProguardR8QualifiedName): ProguardR8QualifiedName? {
    val lexer = ProguardR8Lexer(acceptJavaIdentifiers = true)
    val builder = PsiBuilderFactory.getInstance().createBuilder(ProguardR8ParserDefinition(), lexer, text)
    val ast = ProguardR8Parser().parse(ProguardR8PsiTypes.QUALIFIED_NAME, builder)
    val qualifiedName = ast.psi as? ProguardR8QualifiedName ?: return null
    // Give the new PSI element a parent, otherwise it will be invalid.
    val holder = DummyHolderFactory.createHolder(element.manager, element.language, element)
    holder.treeElement.addChild(ast)

    return qualifiedName
  }

  override fun getRangeInElement(element: ProguardR8QualifiedName): TextRange {
    val firstChildNodeType = element.node.firstChildNode.elementType
    var start = 0
    var end = element.text.length
    if (firstChildNodeType in quotedElements) {
      start += 1
    }

    if (firstChildNodeType in terminatedQuotedElements) {
      end -= 1
    }
    return TextRange(start, end)
  }

  override fun handleContentChange(element: ProguardR8QualifiedName, range: TextRange, newContent: String): ProguardR8QualifiedName {
    val newQualifiedName = element.node.text.replaceRange(IntRange(range.startOffset, range.endOffset - 1), newContent)
    val newElement = createQualifiedNameFromText(newQualifiedName, element)

    return if (newElement != null) element.replace(newElement) as ProguardR8QualifiedName else element
  }
}


class ProguardR8ClassMemberNameManipulator : AbstractElementManipulator<ProguardR8ClassMemberName>() {

  override fun handleContentChange(element: ProguardR8ClassMemberName, range: TextRange, newContent: String): ProguardR8ClassMemberName {
    // Throwing an exception here blocks refactoring, included renames started from a Kotlin file,
    // even if the Shrinker Config File wasn't even open. For the user this means a error message is displayed and refactoring is cancelled.
    // It blocks it just in case name is used in Shrinker Config File.
    if (!ProguardR8Lexer.isJavaIdentifier(newContent)) {
      // After the 2024.2 merge, this identifier (coming from Kotlin) may be surrounding in back-ticks since it's not valid Java. For the
      // error message, it's okay to remove that.
      val identifierForError =
        if (newContent.startsWith('`') && newContent.endsWith('`')) newContent.substring(1, newContent.length - 1)
        else newContent
      throw IncorrectOperationException("\"$identifierForError\" is not an identifier for Shrinker Config.")
    }

    val identifier = element.node.findChildByType(ProguardR8PsiTypes.JAVA_IDENTIFIER) as? LeafPsiElement
    identifier?.replaceWithText(newContent)
    return element
  }
}
