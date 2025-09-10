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

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType

private val LOG = Logger.getInstance("com.android.screenshottest.util.PreviewParser")

internal object FqNames {
  val previewParameter = FqName("androidx.compose.ui.tooling.preview.PreviewParameter")
}

/**
 * Finds all `@Preview` annotations on a given function, recursively resolving custom annotations.
 */
fun findPreviewAnnotations(function: KtNamedFunction): List<KtAnnotationEntry> {
  val previews = mutableListOf<KtAnnotationEntry>()
  val queue = ArrayDeque<KtAnnotationEntry>()
  queue.addAll(function.annotationEntries)
  val visitedFqNames = mutableSetOf<String>()

  while (queue.isNotEmpty()) {
    val annotationEntry = queue.removeFirst()

    if (annotationEntry.shortName?.asString() == "Preview") {
      previews.add(annotationEntry)
      continue
    }

    val uElement = annotationEntry.toUElement()
    val resolvedClass = (uElement as? UAnnotation)?.resolve()

    if (resolvedClass != null) {
      val fqName = resolvedClass.qualifiedName
      if (fqName != null && fqName !in visitedFqNames) {
        visitedFqNames.add(fqName)
        val sourcePsi = resolvedClass.navigationElement
        if (sourcePsi is KtClass) {
          queue.addAll(sourcePsi.annotationEntries)
        }
      }
    }
  }
  return previews
}

/**
 * A helper to find the first composable function called within another function.
 */
fun findComposableCall(function: KtNamedFunction): KtNamedFunction? {
  var resolvedComposable: KtNamedFunction? = null
  val visitor =
    object : KtTreeVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        if (resolvedComposable != null) return

        val resolvedFunction =
          expression.calleeExpression?.references?.firstNotNullOfOrNull { it.resolve() }
            as? KtNamedFunction

        if (resolvedFunction != null) {
          val isComposable =
            resolvedFunction.annotationEntries.any { it.shortName?.asString() == "Composable" }
          if (isComposable) {
            resolvedComposable = resolvedFunction
          }
        }

        if (resolvedComposable == null) {
          super.visitCallExpression(expression)
        }
      }
    }
  function.bodyExpression?.accept(visitor)
  return resolvedComposable
}

/**
 * Extracts the simple name of the provider class from a function's @PreviewParameter.
 */
fun getProviderClassName(function: KtNamedFunction): String? {
  val previewParameter = function.valueParameters.firstOrNull { param ->
    param.annotationEntries.any { it.shortName == FqNames.previewParameter.shortName() }
  } ?: return null

  val annotation = previewParameter.annotationEntries.first { it.shortName == FqNames.previewParameter.shortName() }
  val providerClassArg = annotation.valueArgumentList?.arguments?.firstOrNull()
  return (providerClassArg?.getArgumentExpression() as? KtClassLiteralExpression)
    ?.receiverExpression?.text
}

/**
 * Evaluates a Kotlin expression to a constant value using UAST.
 */
fun evaluateConstantExpression(
  expression: KtExpression?,
  parameterName: String? = null
): String? {
  if (expression == null) return null

  val uExpression = expression.toUElementOfType<UExpression>()
  if (uExpression == null) {
    LOG.warn("Could not convert KtExpression to UExpression for: ${expression.text}")
    return expression.text
  }

  val evaluatedValue = uExpression.evaluate()
  if (evaluatedValue != null) {
    val resolvedString = evaluatedValue.toString()
    val rawText = expression.text.trim('"')

    if (resolvedString != rawText && parameterName != null) {
      LOG.warn(
        "Parameter '$parameterName' resolved from constant. " +
        "Original: '${expression.text}', Resolved: '$resolvedString'"
      )
    }
    return resolvedString
  }

  LOG.warn(
    "UAST could not evaluate expression for parameter '$parameterName': ${expression.text}. " +
    "Falling back to raw text."
  )
  return expression.text
}