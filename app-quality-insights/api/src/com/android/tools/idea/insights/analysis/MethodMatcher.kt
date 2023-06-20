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

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Finds elements within the method specified in the crash frame.
 *
 * Matches if:
 * * [Confidence.MEDIUM] Method contains the "cause" expression on it.(i.e. call to previousFrame's
 *   method, or a throw expression).
 * * [Confidence.LOW] the crash line is within the method.
 */
class MethodMatcher : CrashMatcher {
  override fun match(file: PsiFile, crash: CrashFrame): Match? {
    if (file !is PsiClassOwner) {
      return null
    }
    if (!crash.frame.symbol.startsWith(file.packageName)) {
      return null
    }

    for (cls in file.classes) {
      val innerClasses = ArrayList<UClass>()
      cls
        .toUElement(UClass::class.java)
        ?.accept(
          object : UastVisitor {
            override fun visitClass(node: UClass): Boolean {
              innerClasses.add(node)
              return false
            }

            override fun visitElement(node: UElement) = false
          }
        )
      return innerClasses.mapNotNull { findMatch(it, crash) }.maxByOrNull { it.confidence }
    }
    return null
  }

  private fun findMatch(cls: UClass, crash: CrashFrame): Match? {
    val match: Match? =
      cls.methods
        .asSequence()
        .mapNotNull { method ->
          ProgressManager.checkCanceled()
          val className =
            cls.qualifiedName ?: cls.getContainingUClass()?.qualifiedName ?: return@mapNotNull null
          if (crash.frame.matches(className, method.name)) {
            val result =
              findMatchingCause(
                method,
                crash.cause,
                method.sourcePsi?.textRange ?: return@mapNotNull null
              )
            result?.sourcePsi?.navigationElement?.let {
              return@mapNotNull Match(it, Confidence.MEDIUM, this::class.simpleName!!)
            }
            val line = (crash.frame.line.toInt() - 1).coerceAtLeast(0)
            val psiFile = cls.getContainingUFile()?.sourcePsi ?: return@mapNotNull null
            val startOffset = psiFile.getLineStartOffset(line) ?: return@mapNotNull null
            var endOffset = psiFile.getLineEndOffset(line) ?: return@mapNotNull null
            endOffset = endOffset.coerceAtLeast(startOffset + 1)
            val lineRange = TextRange(startOffset, endOffset)
            if (method.sourcePsi?.textRange?.intersects(lineRange) == true) {
              with(psiFile.elementsInRange(lineRange)) {
                if (isNotEmpty()) {
                  return@mapNotNull Match(this[0], Confidence.LOW, this::class.simpleName!!)
                }
              }
            }
          }
          return@mapNotNull null
        }
        .maxByOrNull { it.confidence }
    return match
  }
}
