// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.compose.debug

import com.android.tools.compose.isComposableLambdaArgument
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import kotlin.LazyThreadSafetyMode.NONE

class ComposeClassNameCalculator : ClassNameCalculator {
  override fun getClassNames(file: KtFile): Map<KtElement, String> {
    val result = mutableMapOf<KtElement, String>()

    val className by lazy(NONE) { computeComposableSingletonsClassName(file) }

    val visitor = object : KtTreeVisitorVoid() {
      override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        try {
          val argument = lambdaExpression.parent as? KtLambdaArgument ?: return
          if (!argument.isComposableLambdaArgument()) return
          result[lambdaExpression] = className
        }
        finally {
          super.visitLambdaExpression(lambdaExpression)
        }
      }
    }

    visitor.visitKtFile(file)
    return result
  }
}
