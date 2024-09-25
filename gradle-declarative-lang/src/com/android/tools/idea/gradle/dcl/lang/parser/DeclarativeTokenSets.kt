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
package com.android.tools.idea.gradle.dcl.lang.parser

import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.BLOCK_COMMENT
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.LINE_COMMENT
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.MULTILINE_STRING_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ONE_LINE_STRING_LITERAL
import com.intellij.psi.tree.TokenSet

object DeclarativeTokenSets {
  val STRING_LITERALS = TokenSet.create(ONE_LINE_STRING_LITERAL, MULTILINE_STRING_LITERAL)
  val COMMENT_TOKENS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)
}