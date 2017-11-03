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
package com.android.tools.idea.smali;

import com.android.tools.idea.smali.parser.SmaliParser;
import com.android.tools.idea.smali.psi.SmaliFile;
import com.android.tools.idea.smali.psi.SmaliTypes;
import com.intellij.lang.ASTNode;
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

import static com.android.tools.idea.smali.SmaliTokenSets.COMMENT_TOKENS;
import static com.android.tools.idea.smali.SmaliTokenSets.STRING_TOKENS;
import static com.intellij.lang.ParserDefinition.SpaceRequirements.MAY;

public class SmaliParserDefinition implements ParserDefinition {
  private static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);

  private static final IFileElementType FILE = new IFileElementType(SmaliLanguage.getInstance());

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new SmaliLexerAdapter();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new SmaliParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens() {
    return WHITE_SPACES;
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return COMMENT_TOKENS;
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return STRING_TOKENS;
  }

  @Override
  @NotNull
  public PsiElement createElement(ASTNode node) {
    return SmaliTypes.Factory.createElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new SmaliFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return MAY;
  }
}
