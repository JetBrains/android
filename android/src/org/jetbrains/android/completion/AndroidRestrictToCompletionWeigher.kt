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
package org.jetbrains.android.completion

import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement

/**
 * A [CompletionWeigher] that decreases the priority of elements annotated with
 * `androidx.annotation.RestrictTo` so they are not suggested at the top when compared with elements
 * of the same name that are not annotated.
 *
 * This is applied in cases like `androidx.xr.runtime.internal.Anchor` vs
 * `androidx.xr.arcore.Anchor` where the latter is not annotated and should have priority over the
 * former.
 */
class AndroidRestrictToCompletionWeigher : CompletionWeigher() {
  enum class Result {
    /** The element is annotated with RestrictTo and should have lower priority. */
    RESTRICTED,
    /** Default priority for any element not annotated with RestrictTo. */
    DEFAULT,
  }

  private fun UAnnotated.hasRestrictToAnnotation(): Boolean {
    if (this.javaPsi is PsiJavaFile) return false // Java files don't have annotations.
    return findAnnotation("androidx.annotation.RestrictTo") != null
  }

  override fun weigh(element: LookupElement, location: CompletionLocation): Result? {
    if (!StudioFlags.RESTRICT_TO_COMPLETION_WEIGHER.get()) return null
    val psiElement = element.psiElement ?: return Result.DEFAULT
    // Only look into files that actually are backed by a library or source code.
    if (!psiElement.isPhysical) return Result.DEFAULT
    // Ignore files that are not Java or Kotlin.
    if (psiElement.language != JavaLanguage.INSTANCE && psiElement.language != KotlinLanguage.INSTANCE) return Result.DEFAULT
    val uElement = psiElement.toUElement() ?: return Result.DEFAULT
    var uAnnotated = uElement.getParentOfType<UAnnotated>(false)
    while (uAnnotated != null) {
      if (uAnnotated.hasRestrictToAnnotation()) return Result.RESTRICTED
      uAnnotated = uAnnotated.getParentOfType<UAnnotated>()
    }
    return Result.DEFAULT
  }
}
