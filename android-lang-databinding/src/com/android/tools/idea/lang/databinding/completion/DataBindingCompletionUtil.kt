/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.TailType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.TailTypeDecorator

/**
 * Returns the [LookupElement] with a Decorator of [type].
 */
fun LookupElementBuilder.withTypeDecorator(type: TailType) = object : TailTypeDecorator<LookupElement>(this) {
  override fun computeTailType(context: InsertionContext) = type

  override fun handleInsert(context: InsertionContext) {
    super.handleInsert(context)
    AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
  }
}