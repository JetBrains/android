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
package com.android.tools.idea.gradle.something.formatting

import com.android.tools.idea.gradle.something.SomethingLanguage
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.ARGUMENTS_LIST
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.ASSIGNMENT
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.BLOCK
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.FACTORY
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.LITERAL
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.OP_COMMA
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.OP_DOT
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.OP_EQ
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.OP_LBRACE
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.OP_LPAREN
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.OP_RBRACE
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.OP_RPAREN
import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.PROPERTY
import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.tree.TokenSet

data class SomethingFormatContext(
  val commonSettings: CommonCodeStyleSettings,
  val spacingBuilder: SpacingBuilder
) {
  companion object {
    fun create(settings: CodeStyleSettings): SomethingFormatContext {
      val commonSettings = settings.getCommonSettings(SomethingLanguage.INSTANCE)
      val elements = TokenSet.create(ASSIGNMENT, FACTORY, BLOCK)
      val builder = SpacingBuilder(commonSettings)
        // factory
        .after(OP_COMMA).spacing(1, 1, 0, false, 0)
        .before(OP_COMMA).spacing(0, 0, 0, false, 0)
        .after(OP_LPAREN).spacing(0, 0, 0, false, 0)
        .before(OP_RPAREN).spacing(0, 0, 0, false, 0)
        // =
        .around(OP_EQ).spacing(1, 1, 0, false, 0)
        // block
        .before(OP_LBRACE).spacing(1, 1, 0, false, 0)
        .after(OP_LBRACE).lineBreakInCode()
        .after(BLOCK).lineBreakInCode()
        .aroundInside(elements, BLOCK).lineBreakInCode()
        .betweenInside(elements, elements, BLOCK).lineBreakInCode()
        // .
        .aroundInside(OP_DOT, TokenSet.create(PROPERTY)).spaceIf(false)

      return SomethingFormatContext(commonSettings, builder)
    }
  }
}