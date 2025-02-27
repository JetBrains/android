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
package com.android.tools.idea.gradle.service.resolve

import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.plugins.gradle.config.GradleBuildscriptSearchScope
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyElement
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyExpression

class PsiPackageGradleUseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? =
    when {
      element is PsiPackage -> GradleBuildscriptSearchScope(element.project)
      else -> null
    }
}

class NamespacePsiPackageReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(groovyPattern("namespace"), NamespacePsiPackageReferenceProvider(ScopeType.MAIN))
    registrar.registerReferenceProvider(groovyPattern("testNamespace"), NamespacePsiPackageReferenceProvider(ScopeType.ANDROID_TEST))
  }
}

class NamespacePsiPackageReferenceProvider(private val scopeType: ScopeType) : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference?> {
    if (element !is GrLiteral) return emptyArray()
    val scope = element.module?.getModuleSystem()?.getResolveScope(scopeType) ?: return emptyArray()
    val references = (element.value as? String)?.let { PackageReferenceSet(it, element, 1, scope).psiReferences } ?: emptyArray()
    return references
  }
}

private fun groovyPattern(text: String) =
  psiElement(GrLiteral::class.java)
    .withParent(
      StandardPatterns.or(
        groovyExpression<GrAssignmentExpression>().withFirstChild(groovyExpression<GrReferenceExpression>().withText(text)),
        groovyElement<GrCommandArgumentList>().withParent(
          groovyExpression<GrApplicationStatement>().withFirstChild(groovyExpression<GrReferenceExpression>().withText(text)))))
