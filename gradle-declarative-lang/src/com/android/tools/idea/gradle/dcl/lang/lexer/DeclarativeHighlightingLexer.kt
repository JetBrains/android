/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.lexer

import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.MULTILINE_STRING_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ONE_LINE_STRING_LITERAL
import com.intellij.lexer.LayeredLexer
import com.intellij.lexer.StringLiteralLexer
import com.intellij.lexer.StringLiteralLexer.NO_QUOTE_CHAR

class DeclarativeHighlightingLexer : LayeredLexer(DeclarativeLexer()) {
  init {
    registerLayer(
      StringLiteralLexer(NO_QUOTE_CHAR, ONE_LINE_STRING_LITERAL, false, null, false, false),
      ONE_LINE_STRING_LITERAL)

    registerLayer(
      StringLiteralLexer(NO_QUOTE_CHAR, MULTILINE_STRING_LITERAL, false, null, false, false),
      MULTILINE_STRING_LITERAL)
  }
}
