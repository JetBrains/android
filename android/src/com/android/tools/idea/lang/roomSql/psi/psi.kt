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
package com.android.tools.idea.lang.roomSql.psi

import com.android.tools.idea.lang.roomSql.ROOM_SQL_FILE_TYPE
import com.android.tools.idea.lang.roomSql.ROOM_SQL_ICON
import com.android.tools.idea.lang.roomSql.ROOM_SQL_LANGUAGE
import com.android.tools.idea.lang.roomSql.parser.RoomSqlLexer
import com.android.tools.idea.lang.roomSql.parser.RoomSqlParser
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.*
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import javax.swing.Icon

class RoomTokenType(debugName: String) : IElementType(debugName, ROOM_SQL_LANGUAGE) {
  override fun toString(): String = "RoomTokenType.${super.toString()}"
}

class RoomAstNodeType(debugName: String) : IElementType(debugName, ROOM_SQL_LANGUAGE) {
  override fun toString(): String = "RoomAstNodeType.${super.toString()}"
}

class RoomSqlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ROOM_SQL_LANGUAGE) {
  override fun getFileType(): FileType = ROOM_SQL_FILE_TYPE
  override fun getIcon(flags: Int): Icon? = ROOM_SQL_ICON
}

val ROOM_SQL_FILE_NODE_TYPE = IFileElementType(ROOM_SQL_LANGUAGE)

private val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
private val COMMENTS = TokenSet.create(RoomPsiTypes.COMMENT)
private val STRING_LITERALS = TokenSet.create(RoomPsiTypes.STRING_LITERAL)

class RoomSqlParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = RoomSqlLexer()
  override fun createParser(project: Project?): PsiParser = RoomSqlParser()
  override fun createElement(node: ASTNode?): PsiElement = RoomPsiTypes.Factory.createElement(node)
  override fun createFile(viewProvider: FileViewProvider): PsiFile = RoomSqlFile(viewProvider)

  override fun getFileNodeType(): IFileElementType = ROOM_SQL_FILE_NODE_TYPE
  override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES
  override fun getCommentTokens(): TokenSet = COMMENTS
  override fun getStringLiteralElements(): TokenSet = STRING_LITERALS
  override fun spaceExistanceTypeBetweenTokens(left: ASTNode?, right: ASTNode?) = ParserDefinition.SpaceRequirements.MAY
}

