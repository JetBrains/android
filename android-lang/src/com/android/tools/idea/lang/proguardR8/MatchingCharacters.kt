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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * [PairedBraceMatcher] for ProguardR8. Makes the IDE insert the matching parenthesis when typing and highlight corresponding pairs of them.
 */
class ProguardR8PairedBraceMatcher : PairedBraceMatcher {
  private val _pairs = arrayOf(
    BracePair(ProguardR8PsiTypes.OPEN_BRACE, ProguardR8PsiTypes.CLOSE_BRACE, true),
    BracePair(ProguardR8PsiTypes.LPAREN, ProguardR8PsiTypes.RPAREN, true)
  )

  override fun getPairs(): Array<BracePair> = _pairs
  override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
  override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}

/**
 * [com.intellij.codeInsight.editorActions.QuoteHandler] for ProguardR8. Makes the IDE insert the matching quote when typing.
 */
class ProguardR8QuoteHandler : SimpleTokenSetQuoteHandler(
  ProguardR8PsiTypes.SINGLE_QUOTED_STRING,
  ProguardR8PsiTypes.DOUBLE_QUOTED_STRING,
  ProguardR8PsiTypes.UNTERMINATED_SINGLE_QUOTED_STRING,
  ProguardR8PsiTypes.UNTERMINATED_DOUBLE_QUOTED_STRING
)
