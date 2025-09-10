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

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A data class holding all the extracted information about a single screenshot test preview.
 */
data class PreviewDetails(
  val function: KtNamedFunction,
  val annotation: KtAnnotationEntry?,
  val allAnnotationsOnFunction: List<KtAnnotationEntry>,
  val composableFunction: KtNamedFunction?,
  val displayNameOverride: String? = null,
  val testId: String? = null
) {
  // The display name is generated once and cached for efficiency.
  val displayName: String by lazy { generateDisplayName() }

  override fun toString(): String = displayName

  /**
   * Generates the display name for the UI, prioritizing the `name` parameter from the `@Preview`
   * annotation.
   */
  private fun generateDisplayName(): String {
    // If a custom name is provided (for multipreview children), use it directly.
    if (displayNameOverride != null) {
      return displayNameOverride
    }

    val functionName = function.name ?: "Unnamed function"
    if (annotation == null) {
      // This case is for functions with no @Preview annotations.
      return functionName
    }

    // 1. Prioritize the explicit 'name' parameter for the main display.
    val nameValue = getArgumentValue("name")?.trim('"')
    if (!nameValue.isNullOrBlank()) {
      return nameValue
    }

    // 2. If no 'name', build a descriptive name from all other parameters.
    val allArgs = annotation.valueArgumentList?.arguments ?: emptyList()
    val descriptiveParts = allArgs
      .mapNotNull { arg ->
        val argName = arg.getArgumentName()?.asName?.identifier
        // Exclude the 'name' parameter since it's handled above.
        if (argName == "name" || argName == null) return@mapNotNull null

        val argValue = arg.getArgumentExpression()?.text ?: ""
        "$argName = $argValue"
      }
      .sorted() // Sort alphabetically for a consistent and predictable order.

    if (descriptiveParts.isNotEmpty()) {
      return descriptiveParts.joinToString(", ")
    }

    // 3. If no descriptive parameters are found, create a fallback name.
    if (allAnnotationsOnFunction.size <= 1) {
      // For a single, unnamed preview, a simple name is best.
      return "Default Preview"
    }

    // 4. Final fallback to an indexed name if all else fails.
    val index = allAnnotationsOnFunction.indexOf(annotation)
    return "Preview ${index + 1}"
  }

  /**
   * Helper to extract the raw textual value of an annotation argument.
   * This is used for display purposes where the unresolved text is more readable.
   */
  private fun getArgumentValue(name: String): String? =
    annotation?.valueArgumentList?.arguments
      ?.find { it.getArgumentName()?.asName?.asString() == name }
      ?.getArgumentExpression()
      ?.text
}