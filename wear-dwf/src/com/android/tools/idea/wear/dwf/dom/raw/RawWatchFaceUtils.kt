/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange

/**
 * Adds an [InsertHandler] that inserts the `[` and `]` around
 * [LookupElementBuilder.getLookupString] after the auto-complete happens, if needed.
 *
 * This helps prevent having the brackets appear in double.
 */
fun LookupElementBuilder.insertBracketsAroundIfNeeded() =
  withInsertHandler { context, lookupElement ->
    val textWithoutBrackets = lookupElement.lookupString.removeSurrounding("[", "]")
    val textWithSurroundingCharacters =
      context.document.getText(TextRange(context.startOffset - 1, context.tailOffset + 1))
    val textWithBrackets = StringBuilder()
    if (!textWithSurroundingCharacters.startsWith("[")) {
      textWithBrackets.append("[")
    }
    textWithBrackets.append(textWithoutBrackets)
    if (!textWithSurroundingCharacters.endsWith("]")) {
      textWithBrackets.append("]")
    }
    context.document.replaceString(
      context.startOffset,
      context.tailOffset,
      textWithBrackets.toString(),
    )
  }
