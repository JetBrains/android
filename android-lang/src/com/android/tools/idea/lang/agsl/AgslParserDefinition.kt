/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lang.agsl

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.lang.agsl.parser.AgslParser
import com.android.tools.idea.lang.agsl.psi.AgslFile
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.EmptyLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.PlainTextParserDefinition
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Temporary delegate to either the [ActualAgslParserDefinition] or the [PlainTextParserDefinition] based on whether AGSL support is
 * enabled.
 */
class AgslParserDefinition :
  ParserDefinition by if (StudioFlags.AGSL_LANGUAGE_SUPPORT.get()) ActualAgslParserDefinition() else PlainTextParserDefinition() {
  fun createLexer(): Lexer =
    if (StudioFlags.AGSL_LANGUAGE_SUPPORT.get()) AgslLexer() else EmptyLexer()
}

private class ActualAgslParserDefinition : ParserDefinition {
  fun createLexer(): Lexer = AgslLexer()
  override fun createLexer(project: Project): Lexer = createLexer()
  override fun createParser(project: Project): PsiParser = AgslParser()
  override fun getFileNodeType(): IFileElementType = AGSL_FILE_ELEMENT_TYPE
  override fun getWhitespaceTokens(): TokenSet = AgslTokenTypeSets.WHITESPACES
  override fun getCommentTokens(): TokenSet = AgslTokenTypeSets.COMMENTS
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  override fun createElement(node: ASTNode): PsiElement = AgslTokenTypes.Factory.createElement(node)
  override fun createFile(viewProvider: FileViewProvider): PsiFile = AgslFile(viewProvider)
  override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements =
    ParserDefinition.SpaceRequirements.MAY
}

private val AGSL_FILE_ELEMENT_TYPE = IFileElementType(AgslLanguage.PRIVATE_AGSL_LANGUAGE)
