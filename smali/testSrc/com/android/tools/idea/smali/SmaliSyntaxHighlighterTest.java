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

import com.intellij.psi.tree.IElementType;
import org.junit.Before;
import org.junit.Test;

import static com.android.tools.idea.smali.SmaliSyntaxHighlighter.*;
import static com.android.tools.idea.smali.SmaliTokenSets.*;
import static com.android.tools.idea.smali.psi.SmaliTypes.JAVA_IDENTIFIER;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SmaliSyntaxHighlighter}.
 */
public class SmaliSyntaxHighlighterTest {
  private SmaliSyntaxHighlighter myHighlighter;

  @Before
  public void setUp() {
    myHighlighter = new SmaliSyntaxHighlighter();
  }

  @Test
  public void getHighlightingLexer() {
    assertThat(myHighlighter.getHighlightingLexer()).isInstanceOf(SmaliLexerAdapter.class);
  }

  @Test
  public void getTokenHighlightsWithKeywordTokens() throws Exception {
    for (IElementType tokenType : KEYWORD_TOKENS.getTypes()) {
      assertSame(KEYWORD_ATTR_KEYS, myHighlighter.getTokenHighlights(tokenType));
    }
  }

  @Test
  public void getTokenHighlightsWithAccessModifiersTokens() throws Exception {
    for (IElementType tokenType : ACCESS_MODIFIER_TOKENS.getTypes()) {
      assertSame(KEYWORD_ATTR_KEYS, myHighlighter.getTokenHighlights(tokenType));
    }
  }

  @Test
  public void getTokenHighlightsWithJavaIdentifierToken() throws Exception {
    assertSame(JAVA_IDENTIFIER_ATTR_KEYS, myHighlighter.getTokenHighlights(JAVA_IDENTIFIER));
  }

  @Test
  public void getTokenHighlightsWithStringTokens() throws Exception {
    for (IElementType tokenType : STRING_TOKENS.getTypes()) {
      assertSame(STRING_ATTR_KEYS, myHighlighter.getTokenHighlights(tokenType));
    }
  }

  @Test
  public void getTokenHighlightsWithNumberTokens() throws Exception {
    for (IElementType tokenType : NUMBER_TOKENS.getTypes()) {
      assertSame(NUMBER_ATTR_KEYS, myHighlighter.getTokenHighlights(tokenType));
    }
  }

  @Test
  public void getTokenHighlightsWithBracesTokens() throws Exception {
    for (IElementType tokenType : BRACES_TOKENS.getTypes()) {
      assertSame(BRACES_ATTR_KEYS, myHighlighter.getTokenHighlights(tokenType));
    }
  }

  @Test
  public void getTokenHighlightsWithParenthesisTokens() throws Exception {
    for (IElementType tokenType : PARENTHESES_TOKENS.getTypes()) {
      assertSame(PARENTHESES_ATTR_KEYS, myHighlighter.getTokenHighlights(tokenType));
    }
  }

  @Test
  public void getTokenHighlightsWithUnrecognizedTokens() throws Exception {
    assertSame(EMPTY_KEYS, myHighlighter.getTokenHighlights(mock(IElementType.class)));
  }
}