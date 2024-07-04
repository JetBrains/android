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

import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BLOCK_COMMENT
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.LINE_COMMENT
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.STRING
import com.android.tools.idea.gradle.declarative.parser.DeclarativeLexer
import com.android.tools.idea.gradle.declarative.parser.DeclarativeParser
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFile
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class DeclarativeParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?) = DeclarativeLexer()
  override fun createParser(project: Project?): PsiParser = DeclarativeParser()

  override fun getFileNodeType(): IFileElementType = FILE_ELEMENT_TYPE

  override fun getCommentTokens(): TokenSet = COMMENT_TOKENS

  override fun getStringLiteralElements(): TokenSet = STRING_LITERAL_TOKENS

  override fun createElement(node: ASTNode?): PsiElement =
    throw UnsupportedOperationException(node?.elementType.toString()) // See DeclarativeASTFactory

  override fun createFile(viewProvider: FileViewProvider): PsiFile = DeclarativeFile(viewProvider)

  companion object {
    val FILE_ELEMENT_TYPE = IFileElementType(DeclarativeLanguage.INSTANCE)
    val COMMENT_TOKENS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)
    val STRING_LITERAL_TOKENS = TokenSet.create(STRING)
  }
}