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
package com.android.tools.idea.wear.preview.lint

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange

/**
 * Retrieves the name of the method containing the [PsiElement] associated with the [highlightInfo]
 * in the given [PsiFile]. This method assumes that only a single method contains the [PsiElement].
 * This method supports both Kotlin and Java.
 */
internal fun PsiFile.containingMethodName(highlightInfo: HighlightInfo) =
  runReadAction {
      elementsInRange(TextRange.create(highlightInfo.startOffset, highlightInfo.endOffset))
    }
    .mapNotNull {
      runReadAction {
        it.parentOfType<PsiMethod>()?.name ?: it.parentOfType<KtNamedFunction>()?.name
      }
    }
    .single()
