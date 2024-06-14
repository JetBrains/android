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
package com.android.tools.compose.aa

import com.android.tools.compose.COMPOSABLE_CALL_TEXT_TYPE
import com.android.tools.compose.isComposableInvocation
import com.android.tools.compose.isComposeEnabled
import com.android.tools.compose.isInLibrarySource
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.highlighting.KotlinCallHighlighterExtension

@Suppress("ContextReceiver")
class ComposableCallHighlighterExtension : KotlinCallHighlighterExtension {
  context(KtAnalysisSession)
  override fun highlightCall(elementToHighlight: PsiElement, call: KtCall): HighlightInfoType? {
    val memberCall = call as? KaCallableMemberCall<*, *> ?: return null
    val callableSymbol = memberCall.symbol
    if (!isComposableInvocation(callableSymbol)) return null

    // For composable invocations, highlight if either:
    // 1. compose is enabled for the current module, or
    // 2. the file is part of a library's source code.
    return if (isComposeEnabled(elementToHighlight) || isInLibrarySource(elementToHighlight))
      COMPOSABLE_CALL_TEXT_TYPE
    else null
  }
}
