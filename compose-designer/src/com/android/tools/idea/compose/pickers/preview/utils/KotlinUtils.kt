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
package com.android.tools.idea.compose.pickers.preview.utils

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

internal fun ResolvedCall<*>.addNewValueArgument(
  newValueArgument: KtValueArgument,
  psiFactory: KtPsiFactory,
): KtValueArgument {
  if (call.valueArgumentList == null) {
    call.callElement.add(psiFactory.createCallArguments("()"))
  }
  return call.valueArgumentList!!.addArgument(newValueArgument)
}

internal fun KtCallElement.addNewValueArgument(
  newValueArgument: KtValueArgument,
  psiFactory: KtPsiFactory,
): KtValueArgument {
  if (valueArguments.isEmpty()) add(psiFactory.createCallArguments("()"))
  return valueArgumentList!!.addArgument(newValueArgument)
}

internal fun KtAnalysisSession.containingPackage(functionSymbol: KtFunctionLikeSymbol) =
  when (functionSymbol) {
    is KtConstructorSymbol -> functionSymbol.containingClassIdIfNonLocal?.packageFqName
    else -> functionSymbol.callableId?.packageName
  }

internal fun KtAnalysisSession.getArgumentForParameter(
  functionCall: KtFunctionCall<*>,
  parameterSymbol: KtValueParameterSymbol,
) =
  functionCall.argumentMapping.entries
    .singleOrNull { (_, parameter) -> parameter.symbol == parameterSymbol }
    ?.key
