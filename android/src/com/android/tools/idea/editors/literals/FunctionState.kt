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
package com.android.tools.idea.editors.literals

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.util.Collections
import java.util.WeakHashMap

/**
 * Keeps track of stateful information about all the functions within a file being edited.
 */
class FunctionState (file: KtFile) {
  data class Offset(var start : Int, var end : Int)
  private val initialOffsets = Collections.synchronizedMap(WeakHashMap<KtFunction, Offset>())

  // This needs to be done within a ReadAction.
  init {
    file.accept(object : PsiRecursiveElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element is KtFunction) {
          updateFunction(element)
        }
        super.visitElement(element)
      }
    })
  }


  private fun updateFunction(function: KtFunction) {
    if (function is KtNamedFunction) {
      // The compose compiler treats the start of a function as the beginning of the fun keyword and does not take annotations into account.
      initialOffsets.putIfAbsent(function, function.funKeyword?.let { Offset(it.startOffset, function.endOffset) })
    } else {
      initialOffsets.putIfAbsent(function, Offset(function.startOffset, function.endOffset))
    }
  }


  fun initialOffsetOf(function : KtFunction) = initialOffsets[function]
}