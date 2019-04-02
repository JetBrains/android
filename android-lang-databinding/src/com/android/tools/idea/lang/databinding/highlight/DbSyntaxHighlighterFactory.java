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
package com.android.tools.idea.lang.databinding.highlight;

import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.AND;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.ANDAND;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.ASTERISK;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.BOOLEAN_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.BYTE_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.CHARACTER_LITERAL;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.CHAR_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.CLASS_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.COLON;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.DEFAULT_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.DIV;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.DOUBLE_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.DOUBLE_LITERAL;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.EQ;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.EQEQ;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.EXCL;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.FALSE;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.FLOAT_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.FLOAT_LITERAL;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.GT;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.GTEQ;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.GTGT;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.GTGTGT;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.INSTANCEOF_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.INTEGER_LITERAL;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.INT_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.LBRACKET;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.LE;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.LONG_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.LONG_LITERAL;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.LPARENTH;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.LT;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.LTLT;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.MINUS;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.NE;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.NULL;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.OR;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.OROR;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.PLUS;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.QUEST;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.QUESTQUEST;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.RBRACKET;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.RESOURCE_REFERENCE;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.RPARENTH;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.SHORT_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.STRING_LITERAL;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.TILDE;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.TRUE;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.VOID_KEYWORD;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.XOR;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.BRACKETS;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.KEYWORD;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.NUMBER;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.OPERATION_SIGN;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.PARENTHESES;
import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.STRING;

import com.android.tools.idea.lang.databinding._DbLexer;
import com.android.tools.idea.lang.databinding.psi.DbTokenTypes;
import com.google.common.collect.Maps;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DbSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @NotNull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    return new DbSyntaxHighlighter();
  }

  private static class DbSyntaxHighlighter extends SyntaxHighlighterBase {

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
      return new FlexAdapter(new _DbLexer(null));
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
      return pack(sAttributes.get(tokenType));
    }

    private static final Map<IElementType, TextAttributesKey> sAttributes = Maps.newHashMapWithExpectedSize(50);

    static {
      final TokenSet keywords = TokenSet
        .create(BOOLEAN_KEYWORD, BYTE_KEYWORD, CHAR_KEYWORD, SHORT_KEYWORD, INT_KEYWORD, LONG_KEYWORD, FLOAT_KEYWORD, DOUBLE_KEYWORD,
                VOID_KEYWORD, CLASS_KEYWORD, INSTANCEOF_KEYWORD, DEFAULT_KEYWORD, TRUE, FALSE, NULL);
      final TokenSet brackets = TokenSet.create(LBRACKET, RBRACKET);
      final TokenSet parentheses = TokenSet.create(LPARENTH, RPARENTH);
      final TokenSet numbers = TokenSet.create(INTEGER_LITERAL, FLOAT_LITERAL, LONG_LITERAL, DOUBLE_LITERAL);
      final TokenSet operators = TokenSet
        .create(EQEQ, NE, LE, LTLT, LT, GTGTGT, GTGT, GTEQ, GT, EQ, EXCL, TILDE, QUESTQUEST, QUEST, COLON, PLUS, MINUS, ASTERISK, DIV,
                ANDAND, AND, OROR, OR, XOR);
      final TokenSet strings = TokenSet.create(STRING_LITERAL, CHARACTER_LITERAL);

      fillMap(sAttributes, keywords, KEYWORD);
      fillMap(sAttributes, brackets, BRACKETS);
      fillMap(sAttributes, parentheses, PARENTHESES);
      fillMap(sAttributes, numbers, NUMBER);
      fillMap(sAttributes, operators, OPERATION_SIGN);
      fillMap(sAttributes, strings, STRING);
      sAttributes.put(RESOURCE_REFERENCE, MARKUP_ATTRIBUTE);
      sAttributes.put(DbTokenTypes.IDENTIFIER, DefaultLanguageHighlighterColors.IDENTIFIER);
      sAttributes.put(DbTokenTypes.DOT, DefaultLanguageHighlighterColors.DOT);
      sAttributes.put(DbTokenTypes.COMMA, DefaultLanguageHighlighterColors.COMMA);
    }
  }
}
