/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;

public interface AidlTokenTypeSets {
  TokenSet WHITESPACES = TokenSet.create(TokenType.WHITE_SPACE);

  TokenSet COMMENTS = TokenSet.create(AidlTokenTypes.COMMENT, AidlTokenTypes.BLOCK_COMMENT);

  TokenSet BAD_TOKENS = TokenSet.create(TokenType.BAD_CHARACTER);

  TokenSet KEY_WORDS = TokenSet
    .create(AidlTokenTypes.BOOLEAN_KEYWORD, AidlTokenTypes.BYTE_KEYWORD, AidlTokenTypes.CHAR_KEYWORD, AidlTokenTypes.DOUBLE_KEYWORD,
            AidlTokenTypes.FLATTENABLE_KEYWORD, AidlTokenTypes.FLOAT_KEYWORD, AidlTokenTypes.IMPORT_KEYWORD, AidlTokenTypes.IN_KEYWORD,
            AidlTokenTypes.INOUT_KEYWORD, AidlTokenTypes.INT_KEYWORD, AidlTokenTypes.INTERFACE_KEYWORD, AidlTokenTypes.LONG_KEYWORD,
            AidlTokenTypes.ONEWAY_KEYWORD, AidlTokenTypes.OUT_KEYWORD, AidlTokenTypes.PACKAGE_KEYWORD, AidlTokenTypes.PARCELABLE_KEYWORD,
            AidlTokenTypes.RPC_KEYWORD, AidlTokenTypes.SHORT_KEYWORD, AidlTokenTypes.VOID_KEYWORD);

}
