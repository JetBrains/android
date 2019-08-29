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
package com.android.tools.idea.lang.proguardR8.parser

import com.android.tools.idea.lang.proguardR8.psi.PROGUARD_R8_FILE_NODE_TYPE
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiFile
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class ProguardR8ParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = ProguardR8Lexer()
  override fun createParser(project: Project?): PsiParser = ProguardR8Parser()
  override fun createElement(node: ASTNode?): PsiElement = ProguardR8PsiTypes.Factory.createElement(node)
  override fun createFile(viewProvider: FileViewProvider): PsiFile = ProguardR8PsiFile(viewProvider)

  override fun getFileNodeType(): IFileElementType = PROGUARD_R8_FILE_NODE_TYPE
  override fun getCommentTokens(): TokenSet = TokenSet.create(ProguardR8PsiTypes.LINE_CMT)
  override fun getStringLiteralElements(): TokenSet = TokenSet.create(ProguardR8PsiTypes.JAVA_IDENTIFIER)
  override fun spaceExistenceTypeBetweenTokens(left: ASTNode?, right: ASTNode?) = ParserDefinition.SpaceRequirements.MAY
}
