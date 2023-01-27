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
package com.android.tools.idea.lang.multiDexKeep;

import com.android.tools.idea.lang.multiDexKeep.parser.MultiDexKeepParser;
import com.android.tools.idea.lang.multiDexKeep.psi.MultiDexKeepFile;
import com.android.tools.idea.lang.multiDexKeep.psi.MultiDexKeepPsiTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class MultiDexKeepParserDefinition implements ParserDefinition {

  private static class TokenSets {
    static final TokenSet WHITE_SPACES = TokenSet.create(MultiDexKeepPsiTypes.EOL);
    static final TokenSet STRING_LITERALS = TokenSet.create(MultiDexKeepPsiTypes.STRING);
  }

  public static final IFileElementType FILE = new IFileElementType(MultiDexKeepLanguage.INSTANCE);

  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new MultiDexKeepLexerAdapter();
  }

  @NotNull
  @Override
  public PsiParser createParser(Project project) {
    return new MultiDexKeepParser();
  }

  @NotNull
  @Override
  public IFileElementType getFileNodeType() {
    return FILE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return TokenSets.WHITE_SPACES;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return TokenSets.STRING_LITERALS;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    return MultiDexKeepPsiTypes.Factory.createElement(node);
  }

  @NotNull
  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new MultiDexKeepFile(viewProvider);
  }
}
