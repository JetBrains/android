/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService

/**
 * Computes argument mapping from arguments to parameters (or returns
 * null if the mapping is 1-1, e.g. in Java), or if the mapping is trivial
 * (Kotlin 0 or 1 args), or if there's some kind of error.
 */
fun computeKotlinArgumentMapping(call: UCallExpression, method: PsiMethod):
  Map<UExpression, PsiParameter>? {
  // Kotlin? If not, mapping is trivial
  val receiver = call.psi as? KtElement ?: return null

  if (method.parameterList.parametersCount < 2) {
    // When there is at most one parameter the mapping is easy to figure out!
    return null
  }

  val service = ServiceManager.getService(
    receiver.project,
    KotlinUastResolveProviderService::class.java
  ) ?: return null
  val bindingContext = service.getBindingContext(receiver)
  val parameters = method.parameterList.parameters
  val resolvedCall = receiver.getResolvedCall(bindingContext) ?: return null
  val valueArguments = resolvedCall.valueArguments
  val elementMap = mutableMapOf<PsiElement, UExpression>()
  for (parameter in call.valueArguments) {
    elementMap[parameter.psi ?: continue] = parameter
  }
  if (valueArguments.isNotEmpty()) {
    var firstParameterIndex = 0
    // Kotlin extension method? Not included in valueArguments indices.
    if (parameters.isNotEmpty() && "\$receiver" == parameters[0].name) {
      firstParameterIndex++
    }

    val mapping = mutableMapOf<UExpression, PsiParameter>()
    for ((parameterDescriptor, valueArgument) in valueArguments) {
      for (argument in valueArgument.arguments) {
        val expression = argument.getArgumentExpression() ?: continue
        val arg = elementMap[expression as PsiElement] ?: continue  // cast only needed to avoid Kotlin compiler frontend bug KT-24309.
        val index = firstParameterIndex + parameterDescriptor.index
        if (index < parameters.size) {
          mapping[arg] = parameters[index]
        }
      }
    }

    if (mapping.isNotEmpty()) {
      return mapping
    }
  }

  return null
}
