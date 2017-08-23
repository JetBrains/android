/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.lang.rs;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class RenderscriptParserDefinition implements ParserDefinition {
  private static final TokenSet WHITESPACE_TOKENS = TokenSet.create(TokenType.WHITE_SPACE);
  private static final TokenSet COMMENT_TOKENS = TokenSet.create(RenderscriptTokenType.COMMENT);
  private static final TokenSet STRING_TOKENS = TokenSet.create(RenderscriptTokenType.STRING);

  public static final IFileElementType FILE =
    new IFileElementType(Language.findInstance(RenderscriptLanguage.class));

  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new RenderscriptLexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new RenderscriptParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return FILE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return WHITESPACE_TOKENS;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return COMMENT_TOKENS;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return STRING_TOKENS;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    return new ASTWrapperPsiElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new RenderscriptFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MUST;
  }
}
