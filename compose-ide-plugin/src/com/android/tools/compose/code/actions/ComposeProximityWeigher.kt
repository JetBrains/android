/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.compose.code.actions

import com.android.tools.compose.isComposableFunction
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityWeigher
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/** This weigher is used in determining the order of items in Kotlin's "Add Imports" list. */
class ComposeProximityWeigher: ProximityWeigher() {
  override fun weigh(element: PsiElement, location: ProximityLocation): Int? {
    if (location.position?.language != KotlinLanguage.INSTANCE) return null
    if (location.positionModule?.getModuleSystem()?.usesCompose != true) return null

    // In the "Add Import" case, `location` unfortunately points only to the file we're in -- so we can't do anything smart that would take
    // into account the position of the code that needs the import.

    // If we've manually weighted this element, use that weight.
    element.manualWeight()?.let { return it }

    if (element.isComposableFunction()) return COMPOSABLE_METHOD_WEIGHT

    return DEFAULT_WEIGHT
  }
}

// Order imports as follows:
// [Weight 2] Any FQ we are promoting
// [Weight 1] Any Composable method
// [Weight 0] Default case
private const val PROMOTED_WEIGHT = 2
private const val COMPOSABLE_METHOD_WEIGHT = 1
private const val DEFAULT_WEIGHT = 0

private fun PsiElement.manualWeight(): Int? {
  val fqName = (this as? KtNamedDeclaration)?.fqName?.asString() ?: return null
  return MANUALLY_WEIGHTED_FQ_NAMES[fqName]
}

private val MANUALLY_WEIGHTED_FQ_NAMES = mapOf(
  "androidx.compose.ui.Modifier" to PROMOTED_WEIGHT,
  "androidx.compose.ui.graphics.Color" to PROMOTED_WEIGHT,
)
