/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.util

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Generates a unique identifier for a test case.
 * Example: MyTest.GreetingPreview_previewName_{...}
 */
fun getIdentifier(details: PreviewDetails): String? {
  val function = details.function
  val containingClass = PsiTreeUtil.getParentOfType(function, KtClass::class.java)

  val simpleClassName = if (containingClass != null) {
    containingClass.name
  } else {
    function.containingKtFile.name.removeSuffix(".kt") + "Kt"
  }

  if (simpleClassName == null) {
    return null
  }

  val functionName = function.name ?: return null

  // Handle @PreviewParameter for multipreview tests
  val previewParameter = function.valueParameters.firstOrNull { param ->
    param.annotationEntries.any { it.shortName == FqNames.previewParameter.shortName() }
  }
  if (previewParameter != null) {
    val annotation = previewParameter.annotationEntries.first { it.shortName == FqNames.previewParameter.shortName() }
    val providerClassArg = annotation.valueArgumentList?.arguments?.firstOrNull()
    val providerClassName = (providerClassArg?.getArgumentExpression() as? KtClassLiteralExpression)
      ?.receiverExpression?.text

    if (providerClassName != null) {
      return "$simpleClassName.${functionName}_[{provider=$providerClassName}]_"
    }
  }

  val annotation = details.annotation ?: return "$simpleClassName.$functionName"

  val isSinglePreview = details.allAnnotationsOnFunction.size == 1
  val testNameBuilder = StringBuilder(functionName)

  if (isSinglePreview) {
    val nameValue = evaluateConstantExpression(
      annotation.valueArgumentList?.arguments
        ?.find { it.getArgumentName()?.asName?.asString() == "name" }
        ?.getArgumentExpression(),
      "name"
    )
    if (!nameValue.isNullOrBlank()) {
      testNameBuilder.append("_").append(nameValue)
    }
  } else {
    val providedArgs = mutableMapOf<String, String>()
    annotation.valueArgumentList?.arguments?.forEach { arg ->
      val name = arg.getArgumentName()?.asName?.identifier ?: return@forEach
      val expression = arg.getArgumentExpression() ?: return@forEach
      val value = evaluateConstantExpression(expression, name)
      if (value != null) {
        providedArgs[name] = value
      }
    }

    val previewName = providedArgs.remove("name") ?: ""
    if (previewName.isNotEmpty()) {
      testNameBuilder.append("_").append(previewName)
    }

    if (providedArgs.isNotEmpty()) {
      val paramsString = providedArgs.toSortedMap().entries.joinToString(", ") { "${it.key}=${it.value}" }
      testNameBuilder.append("_{").append(paramsString).append("}")
    }
  }

  return "$simpleClassName.${testNameBuilder.toString()}"
}