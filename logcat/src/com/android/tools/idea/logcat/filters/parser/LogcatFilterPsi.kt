/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters.parser

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lexer.FlexAdapter
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import icons.StudioIcons
import javax.swing.Icon

/**
 * PSI Definitions for the Logcat Filter language.
 */

internal object LogcatFilterLanguage : Language("LogcatFilter") {
  // This is required for tests. In prod, the language is registered with FileTypeManager but in tests, it's not. It's much easier to
  // hardcode this here than to mock the FileTypeManager in tests, and it has no adverse effects in production code.
  override fun getAssociatedFileType(): LanguageFileType = LogcatFilterFileType
}

internal object LogcatFilterFileType : LanguageFileType(LogcatFilterLanguage) {
  override fun getName() = "Logcat Filter File"

  override fun getDescription() = "Logcat filter language file"

  override fun getDefaultExtension() = "lcf"

  override fun getIcon(): Icon = StudioIcons.Shell.ToolWindows.LOGCAT
}

internal class LogcatFilterTokenType(debugName: String) : IElementType(debugName, LogcatFilterLanguage) {
  override fun toString(): String = "LogcatFilterTokenType." + super.toString()
}

internal class LogcatFilterElementType(debugName: String) : IElementType(debugName, LogcatFilterLanguage)

internal class LogcatFilterLexerAdapter : FlexAdapter(LogcatFilterLexerWrapper())

internal class LogcatFilterFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, LogcatFilterLanguage) {
  override fun getFileType() = LogcatFilterFileType

  override fun toString() = "Logcat Filter File"
}

internal class LogcatFilterParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?) = LogcatFilterLexerAdapter()

  override fun createParser(project: Project?) = LogcatFilterParser()

  override fun getFileNodeType() = FILE

  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

  override fun getWhitespaceTokens() = WHITE_SPACES

  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun createElement(node: ASTNode?): PsiElement = LogcatFilterTypes.Factory.createElement(node)

  override fun createFile(viewProvider: FileViewProvider) = LogcatFilterFile(viewProvider)

  override fun spaceExistenceTypeBetweenTokens(left: ASTNode?, right: ASTNode?) = ParserDefinition.SpaceRequirements.MAY

  companion object {
    val FILE = IFileElementType(LogcatFilterLanguage)
    val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
  }
}
