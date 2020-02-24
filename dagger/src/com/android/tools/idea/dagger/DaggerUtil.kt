/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

const val DAGGER_MODULE_ANNOTATION = "dagger.Module"
const val DAGGER_PROVIDES_ANNOTATION = "dagger.Provides"
const val DAGGER_BINDS_ANNOTATION = "dagger.Binds"
const val INJECT_ANNOTATION = "javax.inject.Inject"

/**
 * Returns all @Module-annotated classes in given [scope].
 */
private fun getDaggerModules(scope: GlobalSearchScope): Query<PsiClass> {
  val scopeAnnotationClass = JavaPsiFacade.getInstance(scope.project).findClass(DAGGER_MODULE_ANNOTATION, scope) ?: return EmptyQuery()
  return AnnotatedElementsSearch.searchPsiClasses(scopeAnnotationClass, scope)
}

/**
 * Returns all Dagger providers (@Provide/@Binds-annotated methods, @Inject-annotated constructors) for given [type] within given [scope].
 */
fun getDaggerProvidersForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiMethod> {
  return getDaggerProvidesMethodsForType(type, scope) +
         getDaggerBindsMethodsForType(type, scope) +
         getDaggerInjectedConstructorsForType(type, scope)
}

/**
 * Returns all @Inject-annotated constructors for given [type] within [scope].
 */
private fun getDaggerInjectedConstructorsForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiMethod> {
  val clazz = (type as? PsiClassType)?.resolve() ?: return emptyList()
  return clazz.constructors.filter { it.isInjected }
}

/**
 * True if PsiMethod belongs to a class annotated with @Module.
 */
private val PsiMethod.isInDaggerModule: Boolean
  get() = containingClass?.hasAnnotation(DAGGER_MODULE_ANNOTATION) == true

/**
 * Returns all @Provide-annotated methods that return given [type] within [scope].
 */
private fun getDaggerProvidesMethodsForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiMethod> {
  return getMethodsWithAnnotation(DAGGER_PROVIDES_ANNOTATION, scope).filter { it.returnType == type && it.isInDaggerModule }
}

/**
 * Returns all @Binds-annotated methods that return given [type] within [scope].
 */
private fun getDaggerBindsMethodsForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiMethod> {
  return getMethodsWithAnnotation(DAGGER_BINDS_ANNOTATION, scope).filter { it.returnType == type && it.isInDaggerModule }
}

/**
 * Returns all methods with [annotationName] within [scope].
 */
private fun getMethodsWithAnnotation(annotationName: String, scope: GlobalSearchScope): Query<PsiMethod> {
  val annotationClass = JavaPsiFacade.getInstance(scope.project).findClass(annotationName, scope) ?: return EmptyQuery()
  return AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
}

/**
 * True if PsiModifierListOwner has @Inject annotation.
 */
private val PsiModifierListOwner.isInjected get() = hasAnnotation(INJECT_ANNOTATION)

/**
 * True if KtProperty has @Inject annotation.
 */
private val KtProperty.isInjected get() = findAnnotation(FqName(INJECT_ANNOTATION)) != null

/**
 * True if PsiElement is @Provide-annotated method.
 */
private val PsiElement?.isProvidesMethod: Boolean
  get() {
    return this is PsiMethod && (hasAnnotation(DAGGER_PROVIDES_ANNOTATION)) ||
           this is KtFunction && findAnnotation(FqName(DAGGER_PROVIDES_ANNOTATION)) != null
  }

/**
 * True if PsiElement is Binds-annotated method.
 */
private val PsiElement?.isBindsMethod: Boolean
  get() {
    return this is PsiMethod && (hasAnnotation(DAGGER_BINDS_ANNOTATION)) ||
           this is KtFunction && findAnnotation(FqName(DAGGER_BINDS_ANNOTATION)) != null
  }

/**
 * True if PsiElement is @Inject-annotated constructor.
 */
private val PsiElement?.isInjectedConstructor: Boolean
  get() = this is PsiMethod && isConstructor && isInjected ||
          this is KtConstructor<*> && (this as? KtAnnotated)?.findAnnotation(FqName(INJECT_ANNOTATION)) != null

/**
 * True if PsiElement is Dagger provider i.e @Provides/@Binds-annotated method or @Inject-annotated constructor.
 */
val PsiElement?.isDaggerProvider get() = isProvidesMethod || isBindsMethod || isInjectedConstructor

/**
 * True if PsiElement is Dagger consumer i.e @Inject-annotated field or param of Dagger provider, see [isDaggerProvider].
 */
val PsiElement?.isDaggerConsumer: Boolean
  get() {
    return this is PsiField && isInjected ||
           this is KtProperty && isInjected ||
           this is PsiParameter && declarationScope.isDaggerProvider ||
           this is KtParameter && this.ownerFunction.isDaggerProvider
  }
