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
package com.android.tools.idea.gradle.declarative.parser

import com.android.tools.idea.gradle.declarative.DeclarativeHighlighter
import com.android.tools.idea.gradle.declarative.DeclarativeTextAttributes
import com.google.common.truth.Truth
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.tree.IElementType
import org.junit.Assert
import org.junit.Test

class DeclarativeHighlighterTest {

  private var myHighlighter = DeclarativeHighlighter()

  @Test
  fun getHighlightingLexer() {
    Truth.assertThat(myHighlighter.getHighlightingLexer()).isInstanceOf(DeclarativeHighlightingLexer::class.java)
  }

  @Test
  fun getTokenHighlights() {
    checkMapping(DeclarativeElementTypeHolder.STRING, DeclarativeTextAttributes.STRING.keys)
    checkMapping(DeclarativeElementTypeHolder.NUMBER, DeclarativeTextAttributes.NUMBER.keys)
    checkMapping(DeclarativeElementTypeHolder.LINE_COMMENT, DeclarativeTextAttributes.LINE_COMMENT.keys)
    checkMapping(DeclarativeElementTypeHolder.NULL, DeclarativeTextAttributes.KEYWORD.keys)
    checkMapping(DeclarativeElementTypeHolder.BOOLEAN, DeclarativeTextAttributes.KEYWORD.keys)
  }


  private fun checkMapping(tokenType: IElementType, keys: Array<TextAttributesKey>){
    for (key: TextAttributesKey in keys) {
      Assert.assertSame(key, myHighlighter.getTokenHighlights(tokenType).first())
    }
  }
}