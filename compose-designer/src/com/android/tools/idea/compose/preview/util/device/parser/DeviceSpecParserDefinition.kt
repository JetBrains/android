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
package com.android.tools.idea.compose.preview.util.device.parser

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

/** [ParserDefinition] for the Compose Preview device parameter specification. */
class DeviceSpecParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = DeviceSpecLexer()
  override fun createParser(project: Project?): PsiParser = DeviceSpecParser()
  override fun getFileNodeType(): IFileElementType = DEVICE_SPEC_FILE_TYPE
  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  override fun createElement(node: ASTNode?): PsiElement =
    DeviceSpecTypes.Factory.createElement(node)
  override fun createFile(viewProvider: FileViewProvider): PsiFile = DeviceSpecPsiFile(viewProvider)
  override fun spaceExistenceTypeBetweenTokens(left: ASTNode?, right: ASTNode?) =
    ParserDefinition.SpaceRequirements.MAY
}
