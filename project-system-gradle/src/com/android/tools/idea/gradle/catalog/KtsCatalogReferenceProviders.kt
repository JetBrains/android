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
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.gradle.util.findCatalogKey
import com.android.tools.idea.gradle.util.findVersionCatalog
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.references.KotlinPsiReferenceProviderContributor
import org.jetbrains.kotlin.references.KotlinPsiReferenceProviderContributor.ReferenceProvider
import org.toml.lang.psi.TomlFile

class KtsAndroidReferenceProviderContributor : KotlinPsiReferenceProviderContributor<KtDotQualifiedExpression> {
  override val elementClass: Class<out KtDotQualifiedExpression>
    get() = KtDotQualifiedExpression::class.java

  override val referenceProvider: ReferenceProvider<KtDotQualifiedExpression>
    get() = ReferenceProvider { element: KtDotQualifiedExpression ->
      if (!element.containingFile.name.endsWith(".gradle.kts")) return@ReferenceProvider emptyList()
      if (element.isEndOfDotExpression()) {
        val file = findVersionCatalog(element.text, element.project) ?: return@ReferenceProvider emptyList()
        return@ReferenceProvider listOf(KtsDotExpressionVersionCatalogReference(element, file))
      }

      emptyList()
    }
}

class KtsDotExpressionVersionCatalogReference(private val refExpr: KtDotQualifiedExpression, val file: TomlFile)
  : PsiReferenceBase<KtDotQualifiedExpression>(refExpr) {
  override fun resolve(): PsiElement? {
    return findCatalogKey(file, refExpr.text.substringAfter("."))
  }
}

fun KtDotQualifiedExpression.isEndOfDotExpression() =
  (this.parent !is KtDotQualifiedExpression || this.parent.children.lastOrNull() !is KtNameReferenceExpression) &&
  this.hasOnlyNameReferences()

private fun KtDotQualifiedExpression.hasOnlyNameReferences(): Boolean =
  this.children.all {
    when (it) {
      is KtNameReferenceExpression -> true
      is KtDotQualifiedExpression -> it.hasOnlyNameReferences()
      else -> false
    }
  }

fun hasLiveCatalogReference(element: KtDotQualifiedExpression) = element.references.any { ref ->
  ref is KtsDotExpressionVersionCatalogReference && ref.resolve()?.let { it.containingFile is TomlFile } == true
}
