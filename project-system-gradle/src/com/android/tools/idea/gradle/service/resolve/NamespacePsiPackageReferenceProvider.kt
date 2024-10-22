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

import com.android.SdkConstants.EXT_GRADLE_DECLARATIVE
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeElement
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeLiteral
import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns.virtualFile
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.isPlainWithEscapes
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
      element is PsiPackage -> GradleBuildscriptSearchScope(element.project).let {
        if (!DeclarativeStudioSupport.isEnabled()) return@let it
        return@let GlobalSearchScope.union(listOf(it, object : GlobalSearchScope(element.project) {
          override fun contains(file: VirtualFile) = file.name.endsWith(EXT_GRADLE_DECLARATIVE)
          override fun isSearchInLibraries() = false
          override fun isSearchInModuleContent(aModule: Module) = true
          override fun getDisplayName() = "Gradle Declarative Configuration Files"
        }))
      }
      else -> null
    }
}

class GroovyNamespacePsiPackageReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(groovyPattern("namespace"), GroovyNamespacePsiPackageReferenceProvider(ScopeType.MAIN))
    registrar.registerReferenceProvider(groovyPattern("testNamespace"), GroovyNamespacePsiPackageReferenceProvider(ScopeType.ANDROID_TEST))
  }
}

class GroovyNamespacePsiPackageReferenceProvider(private val scopeType: ScopeType) : PsiReferenceProvider() {
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

class KotlinNamespacePsiPackageReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(kotlinPattern("namespace"), KotlinNamespacePsiPackageReferenceProvider(ScopeType.MAIN))
    registrar.registerReferenceProvider(kotlinPattern("testNamespace"), KotlinNamespacePsiPackageReferenceProvider(ScopeType.ANDROID_TEST))
  }
}

class KotlinNamespacePsiPackageReferenceProvider(private val scopeType: ScopeType): PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference?> {
    if (element !is KtStringTemplateExpression) return emptyArray()
    if (!element.isPlainWithEscapes()) return emptyArray()
    val scope = element.module?.getModuleSystem()?.getResolveScope(scopeType) ?: return emptyArray()
    val references = (element.stringValue).let { PackageReferenceSet(it, element, 1, scope).psiReferences }
    return references
  }
}

private fun kotlinPattern(text: String) =
  psiElement(KtStringTemplateExpression::class.java)
    .withParent(psiElement(KtBinaryExpression::class.java).withFirstChild(psiElement(KtReferenceExpression::class.java).withText(text)))
    .inVirtualFile(virtualFile().withExtension("kts"))

private val KtStringTemplateExpression.stringValue: String
  get() = run {
    val sb = StringBuilder()
    this.accept(object: KtTreeVisitorVoid() {
      override fun visitEscapeStringTemplateEntry(entry: KtEscapeStringTemplateEntry) { sb.append(entry.unescapedValue) }
      override fun visitLiteralStringTemplateEntry(entry: KtLiteralStringTemplateEntry) { sb.append(entry.text) }
    })
    return sb.toString()
  }

class DeclarativeNamespacePsiPackageReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    if (DeclarativeStudioSupport.isEnabled()) {
      registrar.registerReferenceProvider(dclPattern("namespace"), DeclarativeNamespacePsiPackageReferenceProvider(ScopeType.MAIN))
      registrar.registerReferenceProvider(dclPattern("testNamespace"), DeclarativeNamespacePsiPackageReferenceProvider(ScopeType.ANDROID_TEST))
    }
  }
}

class DeclarativeNamespacePsiPackageReferenceProvider(private val scopeType: ScopeType): PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference?> {
    if (element !is DeclarativeLiteral) return emptyArray()
    val scope = element.module?.getModuleSystem()?.getResolveScope(scopeType) ?: return emptyArray()
    val references = (element.value as? String)?.let { PackageReferenceSet(it, element, 1, scope).psiReferences } ?: emptyArray()
    return references
  }
}

private fun dclPattern(text: String) =
  psiElement(DeclarativeLiteral::class.java)
    .withParent(psiElement(DeclarativeAssignment::class.java)
                  .withFirstChild(psiElement(DeclarativeElement::class.java).withText(text)))
