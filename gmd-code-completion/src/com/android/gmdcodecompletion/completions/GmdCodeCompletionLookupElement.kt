/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.gmdcodecompletion.completions

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation

/**
 * Store value of the lookup element in code completion suggestion list.
 * Can be used with custom comparator to change the ordering of the suggestion list
 */
class GmdCodeCompletionLookupElement(
  // Value of each of the items in the suggestion list
  val myValue: String = "",
  // Score associated with each item for custom ordering
  val myScore: UInt = 0u,
  private val myInsertHandler: InsertHandler<GmdCodeCompletionLookupElement>? = null,
  private val myPresentation: LookupElementPresentation? = null,
) : LookupElement(), Comparable<LookupElement> {

  constructor(element: LookupElement) : this(
    myValue = element.lookupString, myScore = (element as? GmdCodeCompletionLookupElement)?.myScore ?: 0u
  )

  override fun getLookupString(): String {
    return myValue
  }

  override fun compareTo(other: LookupElement): Int {
    // If the other LookupElement is not GmdCodeCompletionLookupElement, give this higher priority
    val otherElement = other as? GmdCodeCompletionLookupElement ?: return -1
    if (myScore != otherElement.myScore) {
      return if (myScore > otherElement.myScore) -1 else 1
    }
    // Compare string value of the two lookup elements if their scores are the same
    return compareValues(myValue, otherElement.myValue)
  }

  override fun handleInsert(context: InsertionContext) {
    myInsertHandler?.handleInsert(context, this) ?: super.handleInsert(context)
  }

  // Use custom presentation to display trailing text, icons, etc. for lookup element
  override fun renderElement(presentation: LookupElementPresentation) {
    if (this.myPresentation != null) {
      presentation.copyFrom(this.myPresentation)
    }
    super.renderElement(presentation)
  }
}