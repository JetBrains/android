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

import com.android.tools.idea.smali.psi.SmaliTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.smali.psi.SmaliTypes.*;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link SmaliTokenSets}.
 */
public class SmaliTokenSetsTest {
  @Test
  public void keywordTokens() throws Exception {
    IElementType[] types = SmaliTokenSets.KEYWORD_TOKENS.getTypes();
    assertThat(types).asList().containsAllIn(Arrays.asList(TRUE, FALSE));
    assertThat(types).asList().containsAllIn(findTokenSets("DOT_"));
  }

  @Test
  public void accessModifierTokens() throws Exception {
    IElementType[] types = SmaliTokenSets.ACCESS_MODIFIER_TOKENS.getTypes();
    assertThat(types).asList().containsAllIn(findTokenSets("AM_"));
  }

  @Test
  public void commentTokens() {
    IElementType[] types = SmaliTokenSets.COMMENT_TOKENS.getTypes();
    assertThat(types).asList().containsExactly(COMMENT);
  }

  @Test
  public void stringTokens() {
    IElementType[] types = SmaliTokenSets.STRING_TOKENS.getTypes();
    assertThat(types).asList().containsExactly(DOUBLE_QUOTED_STRING, CHAR);
  }

  @Test
  public void numberTokens() {
    IElementType[] types = SmaliTokenSets.NUMBER_TOKENS.getTypes();
    assertThat(types).asList().containsExactly(REGULAR_NUMBER, HEX_NUMBER);
  }

  @Test
  public void bracesTokens() {
    IElementType[] types = SmaliTokenSets.BRACES_TOKENS.getTypes();
    assertThat(types).asList().containsExactly(L_CURLY, R_CURLY);
  }

  @Test
  public void parenthesesTokens() {
    IElementType[] types = SmaliTokenSets.PARENTHESES_TOKENS.getTypes();
    assertThat(types).asList().containsExactly(L_PARENTHESIS, R_PARENTHESIS);
  }

  @NotNull
  private static List<IElementType> findTokenSets(@NotNull String namePrefix) throws IllegalAccessException {
    List<IElementType> tokens = new ArrayList<>();
    for (Field field : SmaliTypes.class.getDeclaredFields()) {
      String name = field.getName();
      if (name.startsWith(namePrefix)) {
        Object value = field.get(null);
        if (value instanceof IElementType) {
          tokens.add((IElementType)value);
        }
      }
    }
    assertThat(tokens).isNotEmpty();
    return tokens;
  }
}