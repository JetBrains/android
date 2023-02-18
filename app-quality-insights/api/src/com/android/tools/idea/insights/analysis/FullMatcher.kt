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
package com.android.tools.idea.insights.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElement

/**
 * Finds elements on the exact line as specified in the crash frame.
 *
 * Matches only if all of the following is true for the given line:
 * * Contains the "cause" expression on it.(i.e. call to previousFrame's method, or a throw
 * expression).
 * * Is within the class and method specified in the crash frame.
 */
class FullMatcher : CrashMatcher {

  override fun match(file: PsiFile, crash: CrashFrame): Match? {
    val line = crash.frame.line.toInt().let { if (it > 0) it - 1 else it }

    val startOffset = file.getLineStartOffset(line) ?: return null
    var endOffset = file.getLineEndOffset(line) ?: return null
    if (endOffset < startOffset) endOffset = startOffset + 1

    val range = TextRange(startOffset, endOffset)

    val elementsOnLine = file.elementsInRange(range)
    for (element in elementsOnLine) {
      val el = findMatchingCause(element.toUElement() ?: continue, crash.cause, range) ?: continue
      generateSequence(el) { it.uastParent }.filterIsInstance<UMethod>().forEach { parent ->
        val className = parent.getContainingUClass()?.qualifiedName
        if (className != null) {
          if (crash.frame.matches(className, parent.name)) {
            val target = el.sourcePsi?.navigationElement
            if (target != null) {
              return Match(target, Confidence.HIGH, this::class.simpleName!!)
            }
          }
        }
      }
    }

    return null
  }
}
