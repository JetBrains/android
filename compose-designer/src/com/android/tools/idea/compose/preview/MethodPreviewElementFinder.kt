/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Parses a method call from a [UCallExpression] and returns a map key -> value where the key is the
 * parameter name and the value is the value of it if it's a literal. If the value is another method call,
 * the method parses the call recursively.
 *
 * For example:
 * ```
 * DataClass1(name = "123", data = DataClass2(name = "456", anInt = 0))
 * ```
 *
 * will return a map containing:
 * ```
 *  name -> "123"
 *  data ->
 *          name -> "456"
 *          anInt -> 0
 * ```
 *
 * parameters with null values are not returned as part of the map.
 *
 * @param call the [UCallExpression] to be analyzed.
 * @param calledMethod if not null, it should point to the [UMethod] that [call] is calling. This allows to extract
 *  the formal parameters. If null, the method will obtain the method from the [call].
 */
private fun callExpressionToDataMap(call: UCallExpression, calledMethod: UMethod? = null): Map<String, Any> {
  val method = calledMethod ?: call.resolveToUElement() as? UMethod ?: throw IllegalArgumentException(
    "No calledMethod given and the UCallExpression can not be resolved to a UMethod")

  return method.uastParameters.mapIndexedNotNull { index, uParameter ->
    val name = uParameter.name ?: return@mapIndexedNotNull null

    val argumentValue: Any? = when (val argument = call.getArgumentForParameter(index)) {
      is ULiteralExpression -> argument.evaluate()
      is UCallExpression -> {
        val argumentValues = callExpressionToDataMap(argument)
        if (argumentValues.isNotEmpty()) argumentValues else null
      }
      else -> null
    }

    if (argumentValue != null) Pair(name, argumentValue) else null
  }.toMap()
}

/**
 * A [PreviewElementFinder] that obtains the [PreviewElement] from calls to a [PREVIEW_NAME] method. For example, for the following code:
 * ```
 * @Composable
 * fun ButtonPreview() {
 *  Preview(name = "Button Preview") {
 *    Button("Text")
 *   }
 * }
 * ```
 *
 * one [PreviewElement] with name "Button Preview" will be returned.
 */
object MethodPreviewElementFinder : PreviewElementFinder {
  override fun findPreviewMethods(uFile: UFile): Set<PreviewElement> {
    val previewElements = mutableSetOf<PreviewElement>()
    uFile.accept(object : UastVisitor {
      override fun visitElement(node: UElement) = false

      /**
       * Called when a Preview method call is found. The received node is guaranteed to be called "Preview"
       */
      private fun visitPreviewMethodCall(composableMethodName: String, previewName: String, configuration: Map<String, Any>) =
        previewElements.add(PreviewElement(previewName, composableMethodName,
                                           PreviewConfiguration(apiLevel = (configuration["apiLevel"] as? Int) ?: UNDEFINED_API_LEVEL,
                                                                theme = (configuration["theme"] as? String),
                                                                width = configuration["width"] as? Int ?: UNDEFINED_DIMENSION,
                                                                height = configuration["height"] as? Int ?: UNDEFINED_DIMENSION)))

      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (node.methodName != PREVIEW_NAME) {
          // Keep looking for expressions in case there are other Prevew calls
          return false
        }

        val composableMethod = node.getParentOfType<UMethod>() ?: return false
        val composableMethodClass = composableMethod.uastParent as UClass
        val composableMethodName = "${composableMethodClass.qualifiedName}.${composableMethod.name}"

        val previewUMethod = node.resolveToUElement() as? UMethod ?: return false
        if (previewUMethod.getContainingUFile()?.packageName != PREVIEW_PACKAGE) {
          // This is not the Preview method we are looking for
          return false
        }

        val parameters = callExpressionToDataMap(node, previewUMethod)
        visitPreviewMethodCall(composableMethodName,
                               parameters["name"] as? String ?: "",
                               parameters["configuration"] as? Map<String, Any> ?: emptyMap())

        return super.visitCallExpression(node)
      }
    })

    return previewElements
  }
}