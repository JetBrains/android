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
package com.android.tools.idea.logcat.filters

import com.android.tools.idea.logcat.LogcatBundle.message
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLiteralExpression
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.psi.PsiElement

/**
 * An [Annotator] that highlights errors in the
 * [com.android.tools.idea.logcat.filters.parser.LogcatFilterLanguage]
 */
internal class LogcatFilterErrorAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is LogcatFilterLiteralExpression) {
      when (element.firstChild.text) {
        "level:" ->
          element.lastChild.checkForError(
            holder,
            String::isValidLogLevel,
            message("logcat.filter.error.log.level"),
          )
        "age:" ->
          element.lastChild.checkForError(
            holder,
            String::isValidLogAge,
            message("logcat.filter.error.duration"),
          )
        "is:" ->
          element.lastChild.checkForError(
            holder,
            String::isValidIsFilter,
            message("logcat.filter.error.qualifier"),
          )
      }
    }
  }
}

private fun PsiElement.checkForError(
  holder: AnnotationHolder,
  isValid: (String) -> Boolean,
  message: String,
) {
  if (!isValid(text)) {
    holder
      .newAnnotation(ERROR, message("logcat.filter.error", message, text))
      .range(textRange)
      .create()
  }
}
