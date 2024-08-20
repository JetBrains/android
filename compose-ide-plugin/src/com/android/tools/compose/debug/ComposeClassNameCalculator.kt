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
package com.android.tools.compose.debug

import com.android.tools.compose.isComposableLambdaArgument
import kotlin.LazyThreadSafetyMode.NONE
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class ComposeClassNameCalculator : ClassNameCalculator {
  override fun getClassNames(file: KtFile): Map<KtElement, String> {
    val result = mutableMapOf<KtElement, String>()

    val className by lazy(NONE) { computeComposableSingletonsClassName(file) }
    var lambdaIndex = 0

    val visitor =
      object : KtTreeVisitorVoid() {
        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
          try {
            val argument = lambdaExpression.parent as? KtLambdaArgument ?: return
            if (!argument.isComposableLambdaArgument()) return
            result[lambdaExpression] =
              computeComposableSingletonsLambdaClassName(className, lambdaIndex)
            lambdaIndex++
          } finally {
            super.visitLambdaExpression(lambdaExpression)
          }
        }
      }

    visitor.visitKtFile(file)
    return result
  }
}
