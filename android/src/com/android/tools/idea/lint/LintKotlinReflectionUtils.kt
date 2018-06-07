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

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import java.util.*

// Reflective version of LintKotlinUtils in lint-kotlin.
// Soon this will instead be compiled into the Kotlin plugin and accessed from
// the Android plugin via an extension point.
@Throws(Throwable::class)
fun computeKotlinArgumentMapping(call: UCallExpression, method: PsiMethod):
    Map<UExpression, PsiParameter>? {
  // Kotlin?
  val receiver = call.psi ?: return null
  if (receiver.language == JavaLanguage.INSTANCE) {
    return null
  }
  if (receiver.language.id != "kotlin") {
    return null
  }

  if (method.parameterList.parametersCount < 2) {
    // When there is at most one parameter the mapping is easy to figure out!
    return null
  }

  val loader = receiver.javaClass.classLoader

  val service = ServiceManager.getService(receiver.project,
      Class.forName("org.jetbrains.uast.kotlin.KotlinUastBindingContextProviderService", true, loader)) ?: return null

  val ktElementClass = Class.forName("org.jetbrains.kotlin.psi.KtElement", true, loader)
  val bindingContextClass = Class.forName("org.jetbrains.kotlin.resolve.BindingContext", true, loader)
  val callUtilClass = Class.forName("org.jetbrains.kotlin.resolve.calls.callUtil.CallUtilKt", true, loader)

  val bindingContext = service.javaClass.getMethod("getBindingContext", ktElementClass).invoke(service, receiver)
  val parameters = method.parameterList.parameters
  val resolvedCallMethod = callUtilClass.getDeclaredMethod("getResolvedCall", ktElementClass, bindingContextClass) ?: return null
  val resolvedCall = resolvedCallMethod.invoke(null, receiver, bindingContext) ?: return null
  val valueArgumentsMethod = resolvedCall.javaClass.getDeclaredMethod("getValueArguments")
  val valueArguments = valueArgumentsMethod.invoke(resolvedCall) as Map<*, *>

  val elementMap = mutableMapOf<PsiElement, UExpression>()
  for (parameter in call.valueArguments) {
    elementMap.put(parameter.psi ?: continue, parameter)
  }
  if (valueArguments.isNotEmpty()) {
    var firstParameterIndex = 0
    // Kotlin extension method? Not included in valueArguments indices.
    if (parameters.isNotEmpty() && "\$receiver" == parameters[0].name) {
      firstParameterIndex++
    }

    val valueParameterDescriptorClass = Class.forName("org.jetbrains.kotlin.descriptors.ValueParameterDescriptor", true, loader)
    val getIndexMethod = valueParameterDescriptorClass.getDeclaredMethod("getIndex")
    val valueArgumentClass = Class.forName("org.jetbrains.kotlin.psi.ValueArgument", true, loader)
    val getArgumentExpressionMethod = valueArgumentClass.getDeclaredMethod("getArgumentExpression")
    val resolvedValueArgumentClass = Class.forName("org.jetbrains.kotlin.resolve.calls.model.ResolvedValueArgument", true, loader)
    val getArgumentsMethod = resolvedValueArgumentClass.getDeclaredMethod("getArguments")

    val mapping = HashMap<UExpression, PsiParameter>()
    for ((parameterDescriptor, valueArgument) in valueArguments) {
      val arguments = getArgumentsMethod.invoke(valueArgument) as List<*>
      for (argument in arguments) {
        val expression = getArgumentExpressionMethod.invoke(argument) ?: continue
        val arg = elementMap[expression] ?: continue

        val index = firstParameterIndex + ((getIndexMethod.invoke(parameterDescriptor) as Int))
        if (index < parameters.size) {
          mapping.put(arg, parameters[index])
        }
      }
    }

    if (!mapping.isEmpty()) {
      return mapping
    }

  }

  return null
}
