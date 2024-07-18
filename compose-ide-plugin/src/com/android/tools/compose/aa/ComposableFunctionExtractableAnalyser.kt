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

import androidx.compose.compiler.plugins.kotlin.ComposeClassIds
import com.android.tools.idea.kotlin.findAnnotation
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractFunctionDescriptorModifier
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument

/**
 * Adds [COMPOSABLE_FQ_NAME] annotation to a function when it's extracted from a function annotated
 * with [COMPOSABLE_FQ_NAME] or Composable context.
 */
class ComposableFunctionExtractableAnalyser : ExtractFunctionDescriptorModifier {
  /**
   * Returns true if the type of the given function parameter that takes this [KtLambdaArgument]
   * has @Composable annotation.
   *
   * Example: [KtLambdaArgument] in `myFunction {}` for `fun myFunction(context: @Composable () ->
   * Unit)`
   */
  private fun KtLambdaArgument.isComposable(): Boolean {
    val callExpression = parent as KtCallExpression
    val lambdaExpression = getLambdaExpression() ?: return false
    return analyze(callExpression) {
      val call = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return false
      val parameterTypeForLambda =
        call.argumentMapping[lambdaExpression]?.returnType ?: return false
      parameterTypeForLambda.annotations.classIds.any { it == ComposeClassIds.Composable }
    }
  }

  override fun modifyDescriptor(descriptor: ExtractableCodeDescriptor): ExtractableCodeDescriptor {
    if (descriptor.extractionData.commonParent.getModuleSystem()?.usesCompose != true) {
      return descriptor
    }

    // When a property is being extracted (as in, "Extract constant"), the @Composable annotation
    // does not apply.
    if (descriptor.extractionData.options.extractAsProperty) return descriptor

    val sourceFunction = descriptor.extractionData.targetSibling
    if (sourceFunction is KtAnnotated) {
      sourceFunction.findAnnotation(ComposeClassIds.Composable)?.let {
        return descriptor.copy(
          annotationClassIds = descriptor.annotationClassIds + ComposeClassIds.Composable
        )
      }
    }
    val outsideLambda =
      descriptor.extractionData.commonParent.parentOfType<KtLambdaArgument>(true)
        ?: return descriptor
    return if (outsideLambda.isComposable()) {
      descriptor.copy(
        annotationClassIds = descriptor.annotationClassIds + ComposeClassIds.Composable
      )
    } else {
      descriptor
    }
  }
}
