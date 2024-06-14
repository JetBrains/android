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
import org.jetbrains.kotlin.idea.references.KotlinPsiReferenceRegistrar
import org.jetbrains.kotlin.idea.references.KotlinReferenceProviderContributor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.references.fe10.base.KtFe10KotlinReferenceProviderContributor
import org.toml.lang.psi.TomlFile

// Wrapper for reference provider contributor to add reference provider for Kts to Catalog
class K10KtsAndroidReferenceProviderContributor : KotlinReferenceProviderContributor {
  val contributor = KtFe10KotlinReferenceProviderContributor()
  override fun registerReferenceProviders(registrar: KotlinPsiReferenceRegistrar) {
    contributor.registerReferenceProviders(registrar)
    registerProvider(registrar)
  }
}

// Wrapper for reference provider contributor to add reference provider for Kts to Catalog
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")   // TODO AS Koala Canary 8 merge
class FirKtsAndroidReferenceProviderContributor : KotlinReferenceProviderContributor {
  private val contributor = org.jetbrains.kotlin.analysis.api.fir.references.KotlinFirReferenceContributor()
  override fun registerReferenceProviders(registrar: KotlinPsiReferenceRegistrar) {
    contributor.registerReferenceProviders(registrar)
    registerProvider(registrar)
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

private fun registerProvider(registrar: KotlinPsiReferenceRegistrar) {
  registrar.registerProvider<KtDotQualifiedExpression> provider@{ element: KtDotQualifiedExpression ->
    if (!element.containingFile.name.endsWith(".gradle.kts")) return@provider null
    if (element.isEndOfDotExpression()) {
      val file = findVersionCatalog(element.text, element.project) ?: return@provider null
      return@provider KtsDotExpressionVersionCatalogReference(element, file)
    }

    return@provider null
  }
}