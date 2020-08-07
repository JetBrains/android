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
package com.android.tools.idea.lang.androidSql.parser

import com.android.tools.idea.lang.androidSql.AndroidSqlFileType
import com.android.tools.idea.lang.androidSql.COMMENTS
import com.android.tools.idea.lang.androidSql.STRING_LITERALS
import com.android.tools.idea.lang.androidSql.WHITE_SPACES
import com.android.tools.idea.lang.androidSql.psi.ANDROID_SQL_FILE_NODE_TYPE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil

open class AndroidSqlParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = AndroidSqlLexer()
  override fun createParser(project: Project?): PsiParser = AndroidSqlParser()
  override fun createElement(node: ASTNode?): PsiElement = AndroidSqlPsiTypes.Factory.createElement(node)
  override fun createFile(viewProvider: FileViewProvider): PsiFile = AndroidSqlFile(viewProvider)

  override fun getFileNodeType(): IFileElementType = ANDROID_SQL_FILE_NODE_TYPE
  override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES
  override fun getCommentTokens(): TokenSet = COMMENTS
  override fun getStringLiteralElements(): TokenSet = STRING_LITERALS
  override fun spaceExistenceTypeBetweenTokens(left: ASTNode?, right: ASTNode?) = ParserDefinition.SpaceRequirements.MAY

  companion object {
    @JvmStatic
    fun isValidSqlQuery(project: Project, input: String): Boolean {
      val psiFile = parseSqlQuery(project, input)
      return !PsiTreeUtil.hasErrorElements(psiFile)
    }

    @JvmStatic
    fun parseSqlQuery(project: Project, input: String): PsiFile {
      return PsiFileFactory.getInstance(project).createFileFromText("temp.sql", AndroidSqlFileType.INSTANCE, input)
    }
  }
}
