/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.editor;

import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import javax.annotation.Nullable;

/** Provides quote auto-closing support. */
public class BuildQuoteHandler extends SimpleTokenSetQuoteHandler implements MultiCharQuoteHandler {

  public BuildQuoteHandler() {
    super(BuildToken.fromKind(TokenKind.STRING));
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    if (!myLiteralTokenSet.contains(iterator.getTokenType())) {
      return false;
    }
    int start = iterator.getStart();
    if (offset == start) {
      return true;
    }
    final Document document = iterator.getDocument();
    if (document == null) {
      return false;
    }
    CharSequence text = document.getCharsSequence();
    char theQuote = text.charAt(offset);
    if (offset >= 2
        && text.charAt(offset - 1) == theQuote
        && text.charAt(offset - 2) == theQuote
        && (offset < 3 || text.charAt(offset - 3) != theQuote)) {
      if (super.isOpeningQuote(iterator, offset)) {
        return true;
      }
    }
    return false;
  }

  private static int getLiteralStartOffset(CharSequence text, int start) {
    char c = Character.toUpperCase(text.charAt(start));
    if (c == 'U' || c == 'B') {
      start++;
      c = Character.toUpperCase(text.charAt(start));
    }
    if (c == 'R') {
      start++;
    }
    return start;
  }

  @Override
  protected boolean isNonClosedLiteral(HighlighterIterator iterator, CharSequence chars) {
    int end = iterator.getEnd();
    if (getLiteralStartOffset(chars, iterator.getStart()) >= end - 1) {
      return true;
    }
    char endSymbol = chars.charAt(end - 1);
    if (endSymbol != '"' && endSymbol != '\'') {
      return true;
    }

    //for triple quoted string
    if (end >= 3
        && (endSymbol == chars.charAt(end - 2))
        && (chars.charAt(end - 2) == chars.charAt(end - 3))
        && (end < 4 || chars.charAt(end - 4) != endSymbol)) {
      return true;
    }

    return false;
  }

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();
    if (!myLiteralTokenSet.contains(tokenType)) {
      return false;
    }
    int start = iterator.getStart();
    int end = iterator.getEnd();
    if (end - start >= 1 && offset == end - 1) {
      return true; // single quote
    }
    if (end - start < 3 || offset < end - 3) {
      return false;
    }
    // check for triple quote
    Document doc = iterator.getDocument();
    if (doc == null) {
      return false;
    }
    CharSequence chars = doc.getCharsSequence();
    char quote = chars.charAt(start);
    boolean tripleQuote = quote == chars.charAt(start + 1) && quote == chars.charAt(start + 2);
    if (!tripleQuote) {
      return false;
    }
    for (int i = offset; i < Math.min(offset + 2, end); i++) {
      if (quote != chars.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public CharSequence getClosingQuote(HighlighterIterator iterator, int offset) {
    char theQuote = iterator.getDocument().getCharsSequence().charAt(offset - 1);
    if (super.isOpeningQuote(iterator, offset - 1)) {
      return String.valueOf(theQuote);
    }
    if (super.isOpeningQuote(iterator, offset - 3)) {
      return StringUtil.repeat(String.valueOf(theQuote), 3);
    }
    return null;
  }
}
