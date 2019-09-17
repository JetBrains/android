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
import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.IncorrectOperationException

class ProguardR8QualifiedNameManipulator : AbstractElementManipulator<ProguardR8QualifiedName>() {

  private fun createQualifiedNameFromText(text: String, element: ProguardR8QualifiedName): ProguardR8QualifiedName? {
    val lexer = ProguardR8Lexer(acceptJavaIdentifiers = true)
    val builder = PsiBuilderFactory.getInstance().createBuilder(ProguardR8ParserDefinition(), lexer, text)
    val ast = ProguardR8Parser().parse(ProguardR8PsiTypes.QUALIFIED_NAME, builder)
    val qualifiedName = ast.psi as? ProguardR8QualifiedName ?: return null
    // Give the new PSI element a parent, otherwise it will be invalid.
    val dummyHolder = DummyHolderFactory.createHolder(element.manager, element.language, element)
    dummyHolder.treeElement.addChild(ast)

    return qualifiedName
  }

  override fun handleContentChange(element: ProguardR8QualifiedName, range: TextRange, newContent: String): ProguardR8QualifiedName {
    val newQualifiedName = element.node.text.replaceRange(IntRange(range.startOffset, range.endOffset - 1), newContent)
    val newElement = createQualifiedNameFromText(newQualifiedName, element)

    return if (newElement != null) element.replace(newElement) as ProguardR8QualifiedName else element
  }
}


class ProguardR8ClassMemberManipulator : AbstractElementManipulator<ProguardR8ClassMember>() {

  override fun handleContentChange(element: ProguardR8ClassMember, range: TextRange, newContent: String): ProguardR8ClassMember {
    // It blocks refactoring for class member names that we don't support yet, e.g. "my-method" for Kotlin code.
    // It blocks it just in case class member is used in proguard files.
    if (!ProguardR8Lexer.isJavaIdentifier(newContent)) {
      throw IncorrectOperationException("\"$newContent\" is not an identifier for Proguard/R8 files.")
    }

    val identifier = element.node.findChildByType(ProguardR8PsiTypes.JAVA_IDENTIFIER) as? LeafPsiElement
    identifier?.replaceWithText(newContent)
    return element
  }
}
