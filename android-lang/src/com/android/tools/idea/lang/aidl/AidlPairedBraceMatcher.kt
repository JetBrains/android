/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl

import com.android.tools.idea.lang.aidl.lexer.AidlTokenTypes
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * [PairedBraceMatcher] for AIDL. Makes the IDE insert the matching parenthesis or brace when typing.
 */
class AidlPairedBraceMatcher : PairedBraceMatcher {
  override fun getPairs(): Array<BracePair> = parenPair
  override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
  override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}

private val parenPair = arrayOf(
  BracePair(AidlTokenTypes.LPAREN, AidlTokenTypes.RPAREN, true),
  BracePair(AidlTokenTypes.LBRACKET, AidlTokenTypes.RBRACKET, true)
  // not including LBRACE/RBRACE for now because it's interfering with
  // the keyboard handler, so typing "{" to open a block and hitting
  // results in unmatched braces
)