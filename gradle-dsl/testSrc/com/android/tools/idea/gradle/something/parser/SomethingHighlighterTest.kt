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
package com.android.tools.idea.gradle.something.parser

import com.android.tools.idea.gradle.something.SomethingHighlighter
import com.android.tools.idea.gradle.something.SomethingTextAttributes
import com.google.common.truth.Truth
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.tree.IElementType
import org.junit.Assert
import org.junit.Test

class SomethingHighlighterTest {

  private var myHighlighter = SomethingHighlighter()

  @Test
  fun getHighlightingLexer() {
    Truth.assertThat(myHighlighter.getHighlightingLexer()).isInstanceOf(SomethingHighlightingLexer::class.java)
  }

  @Test
  fun getTokenHighlights() {
    checkMapping(SomethingElementTypeHolder.STRING,SomethingTextAttributes.STRING.keys)
    checkMapping(SomethingElementTypeHolder.NUMBER, SomethingTextAttributes.NUMBER.keys)
    checkMapping(SomethingElementTypeHolder.LINE_COMMENT, SomethingTextAttributes.LINE_COMMENT.keys)
    checkMapping(SomethingElementTypeHolder.NULL, SomethingTextAttributes.KEYWORD.keys)
    checkMapping(SomethingElementTypeHolder.BOOLEAN, SomethingTextAttributes.KEYWORD.keys)
  }


  private fun checkMapping(tokenType: IElementType, keys: Array<TextAttributesKey>){
    for (key: TextAttributesKey in keys) {
      Assert.assertSame(key, myHighlighter.getTokenHighlights(tokenType).first())
    }
  }
}