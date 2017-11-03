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
package com.android.tools.idea.lang.roomSql.parser

import com.android.tools.idea.lang.roomSql.COMMENTS
import com.android.tools.idea.lang.roomSql.STRING_LITERALS
import com.android.tools.idea.lang.roomSql.WHITE_SPACES
import com.android.tools.idea.lang.roomSql.psi.ROOM_SQL_FILE_NODE_TYPE
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes
import com.android.tools.idea.lang.roomSql.psi.RoomSqlFile
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class RoomSqlLexer : FlexAdapter(_RoomSqlLexer()) {
  companion object {
    fun needsQuoting(name: String): Boolean {
      val lexer = RoomSqlLexer()
      lexer.start(name)
      return lexer.tokenType != RoomPsiTypes.IDENTIFIER || lexer.tokenEnd != lexer.bufferEnd
    }

    /** Checks if the given name (table name, column name) needs escaping and returns a string that's safe to put in SQL. */
    fun getValidName(name: String): String =
        if (!needsQuoting(name)) name else "`${name.replace("`", "``")}`"
  }
}

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