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

import com.android.tools.idea.AndroidPsiUtils.toPsiType
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getResolveScope
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

const val DAGGER_MODULE_ANNOTATION = "dagger.Module"
const val DAGGER_PROVIDES_ANNOTATION = "dagger.Provides"
const val DAGGER_BINDS_ANNOTATION = "dagger.Binds"
const val DAGGER_BINDS_INSTANCE_ANNOTATION = "dagger.BindsInstance"
const val INJECT_ANNOTATION = "javax.inject.Inject"
const val DAGGER_COMPONENT_ANNOTATION = "dagger.Component"
const val DAGGER_SUBCOMPONENT_ANNOTATION = "dagger.Subcomponent"
const val DAGGER_SUBCOMPONENT_BUILDER_ANNOTATION = "dagger.Subcomponent.Builder"
const val DAGGER_SUBCOMPONENT_FACTORY_ANNOTATION = "dagger.Subcomponent.Factory"


private const val INCLUDES_ATTR_NAME = "includes"
private const val MODULES_ATTR_NAME = "modules"
private const val DEPENDENCIES_ATTR_NAME = "dependencies"
private const val SUBCOMPONENTS_ATTR_NAME = "subcomponents"

/**
 * Returns all classes annotated [annotationName] in a given [searchScope].
 */
private fun getClassesWithAnnotation(project: Project, annotationName: String, searchScope: SearchScope): Query<PsiClass> {
  val annotationClass = JavaPsiFacade.getInstance(project).findClass(annotationName, GlobalSearchScope.allScope(project))
                        ?: return EmptyQuery()
  return AnnotatedElementsSearch.searchPsiClasses(annotationClass, searchScope)
}

/**
 * Returns all Dagger providers (see [isDaggerProvider] for a [type] with a [qualifierInfo] within a [scope].
 *
 * Null [qualifierInfo] means that binding has not qualifier or has more then one.
 */
private fun getDaggerProviders(type: PsiType, qualifierInfo: QualifierInfo?, scope: GlobalSearchScope): Collection<PsiModifierListOwner> {
  return getDaggerProvidesMethodsForType(type, scope).filterByQualifier(qualifierInfo) +
         getDaggerBindsMethodsForType(type, scope).filterByQualifier(qualifierInfo) +
         getDaggerBindsInstanceMethodsAndParametersForType(type, scope).filterByQualifier(qualifierInfo) +
         getDaggerInjectedConstructorsForType(type)
}

/**
 * Returns all @BindsInstance-annotated methods and params that return given [type] within [scope].
 */
private fun getDaggerBindsInstanceMethodsAndParametersForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiModifierListOwner> {
  return getMethodsWithAnnotation(DAGGER_BINDS_INSTANCE_ANNOTATION, scope).filter { it.returnType == type } +
         getParametersWithAnnotation(DAGGER_BINDS_INSTANCE_ANNOTATION, scope).filter { it.type == type }
}

/**
 * Returns all Dagger providers (@Provide/@Binds-annotated methods, @Inject-annotated constructors) for [element].
 */
fun getDaggerProvidersFor(element: PsiElement): Collection<PsiModifierListOwner> {
  val scope = ModuleUtil.findModuleForPsiElement(element)?.getModuleSystem()?.getResolveScope(element) ?: return emptyList()
  val (type, qualifierInfo) = extractTypeAndQualifierInfo(element) ?: return emptyList()

  return getDaggerProviders(type, qualifierInfo, scope)
}

/**
 * Returns all @Inject-annotated fields of [type] within given [scope].
 */
private fun getInjectedFieldsForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiField> {
  val annotationClass = JavaPsiFacade.getInstance(scope.project).findClass(INJECT_ANNOTATION, scope) ?: return emptyList()
  return AnnotatedElementsSearch.searchPsiFields(annotationClass, scope).filter { it.type == type }
}

/**
 * Returns params of @Provides/@Binds/@Inject-annotated method or @Inject-annotated constructor that have given [type] within given [scope].
 */
private fun getParamsOfDaggerProvidersForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiParameter> {
  val methodsQueries = getMethodsWithAnnotation(INJECT_ANNOTATION, scope) +
                       getMethodsWithAnnotation(DAGGER_BINDS_ANNOTATION, scope) +
                       getMethodsWithAnnotation(DAGGER_PROVIDES_ANNOTATION, scope)
  return methodsQueries.flatMap { it.parameterList.parameters.toList() }.filter { it.type == type }
}

/**
 * Returns all Dagger consumers (see [isDaggerConsumer]) for given [type] with given [qualifierInfo] within [scope].
 *
 * Null [qualifierInfo] means that binding has not qualifier or has more then one.
 */
private fun getDaggerConsumers(type: PsiType, qualifierInfo: QualifierInfo?, scope: GlobalSearchScope): Collection<PsiVariable> {
  return getInjectedFieldsForType(type, scope).filterByQualifier(qualifierInfo) +
         getParamsOfDaggerProvidersForType(type, scope).filterByQualifier(qualifierInfo)
}

/**
 * Returns all Dagger consumers (see [isDaggerConsumer]) for given [element].
 */
fun getDaggerConsumersFor(element: PsiElement): Collection<PsiVariable> {
  val scope = ModuleUtil.findModuleForPsiElement(element)?.getModuleSystem()?.getResolveScope(element) ?: return emptyList()
  val (type, qualifierInfo) = extractTypeAndQualifierInfo(element) ?: return emptyList()

  return getDaggerConsumers(type, qualifierInfo, scope)
}

/**
 * Returns all @Inject-annotated constructors for a given [type].
 */
private fun getDaggerInjectedConstructorsForType(type: PsiType): Collection<PsiMethod> {
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
 * Returns all PsiParameters with [annotationName] within [scope].
 */
private fun getParametersWithAnnotation(annotationName: String, scope: GlobalSearchScope): Query<PsiParameter> {
  val annotationClass = JavaPsiFacade.getInstance(scope.project).findClass(annotationName, scope) ?: return EmptyQuery()
  return AnnotatedElementsSearch.searchPsiParameters(annotationClass, scope)
}

/**
 * True if PsiModifierListOwner has @Inject annotation.
 */
private val PsiModifierListOwner.isInjected get() = hasAnnotation(INJECT_ANNOTATION)

/**
 * True if KtProperty has @Inject annotation.
 */
private val KtAnnotated.isInjected get() = findAnnotation(FqName(INJECT_ANNOTATION)) != null

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

private val PsiElement?.isBindsInstanceMethodOrParameter: Boolean
  get() {
    return this is PsiMethod && hasAnnotation(DAGGER_BINDS_INSTANCE_ANNOTATION) ||
           this is KtFunction && findAnnotation(FqName(DAGGER_BINDS_INSTANCE_ANNOTATION)) != null ||
           this is PsiParameter && hasAnnotation(DAGGER_BINDS_INSTANCE_ANNOTATION) ||
           this is KtParameter && findAnnotation(FqName(DAGGER_BINDS_INSTANCE_ANNOTATION)) != null
  }

/**
 * True if PsiElement is Dagger provider i.e @Provides/@Binds/@BindsInstance-annotated method or @Inject-annotated constructor or
 * @BindsInstance-annotated parameter.
 */
val PsiElement?.isDaggerProvider get() = isProvidesMethod || isBindsMethod || isInjectedConstructor || isBindsInstanceMethodOrParameter

/**
 * True if PsiElement is Dagger consumer i.e @Inject-annotated field or param of Dagger provider, see [isDaggerProvider].
 */
val PsiElement?.isDaggerConsumer: Boolean
  get() {
    return this is KtLightField && lightMemberOrigin?.originalElement?.isInjected == true ||
           this is PsiField && isInjected ||
           this is KtProperty && isInjected ||
           this is PsiParameter && declarationScope.isDaggerProvider ||
           this is KtParameter && this.ownerFunction.isDaggerProvider
  }


internal fun PsiElement?.isClassOrObjectAnnotatedWith(annotationFQName: String): Boolean {
  return this is KtClass && this !is KtEnumEntry && findAnnotation(FqName(annotationFQName)) != null ||
         this is KtObjectDeclaration && !this.isCompanion() && findAnnotation(FqName(annotationFQName)) != null ||
         this is PsiClass && hasAnnotation(annotationFQName)
}

/**
 * True if PsiElement is class annotated DAGGER_MODULE_ANNOTATION.
 */
internal val PsiElement?.isDaggerModule get() = isClassOrObjectAnnotatedWith(DAGGER_MODULE_ANNOTATION)

internal val PsiElement?.isDaggerComponentMethod: Boolean
  get() = this is PsiMethod && this.containingClass.isDaggerComponent ||
          this is KtNamedFunction && this.containingClass().isDaggerComponent

internal val PsiElement?.isDaggerComponent get() = isClassOrObjectAnnotatedWith(DAGGER_COMPONENT_ANNOTATION)

internal val PsiElement?.isDaggerSubcomponent
  get() = isClassOrObjectAnnotatedWith(DAGGER_SUBCOMPONENT_ANNOTATION) ||
          isClassOrObjectAnnotatedWith(DAGGER_SUBCOMPONENT_BUILDER_ANNOTATION) ||
          isClassOrObjectAnnotatedWith(DAGGER_SUBCOMPONENT_FACTORY_ANNOTATION)
/**
 * Returns pair of a type and an optional [QualifierInfo] for [PsiElement].
 *
 * Returns null if it's impossible to extract type.
 */
private fun extractTypeAndQualifierInfo(element: PsiElement): Pair<PsiType, QualifierInfo?>? {
  val type: PsiType =
    when (element) {
      is PsiMethod -> if (element.isConstructor) element.containingClass?.let { toPsiType(it) } else element.returnType
      is KtFunction -> if (element is KtConstructor<*>) element.containingClass()?.toPsiType() else element.psiType
      is PsiField -> element.type
      is KtProperty -> element.psiType
      is PsiParameter -> element.type
      is KtParameter -> element.psiType
      else -> null
    } ?: return null

  return Pair(type, element.getQualifierInfo())
}

/**
 * Returns methods of interfaces annotated [DAGGER_COMPONENT_ANNOTATION] that have the a type and a [QualifierInfo] as a [provider].
 */
fun getDaggerComponentMethodsForProvider(provider: PsiElement): Collection<PsiMethod> {
  val (type, qualifierInfo) = extractTypeAndQualifierInfo(provider) ?: return emptyList()
  val components = getClassesWithAnnotation(provider.project, DAGGER_COMPONENT_ANNOTATION, provider.useScope)
  return components.flatMap {
    // Instantiating methods doesn't have parameters.
    component ->
    component.methods.filter { it.returnType == type && !it.hasParameters() }.filterByQualifier(qualifierInfo)
  }
}

/**
 * Returns interfaces annotated [DAGGER_COMPONENT_ANNOTATION] that are parents to a [subcomponent].
 *
 * A subcomponent can be associated with parent in two ways:
 * 1. Add to the parent Component method that returns the [subcomponent] class or the [subcomponent] factory or builder class.
 * 2. Add the [subcomponent] class to the [SUBCOMPONENTS_ATTR_NAME] attribute of a @Module that the parent component installs.
 *
 * See [Dagger doc](https://dagger.dev/subcomponents.html).
 */
internal fun getDaggerParentComponentsForSubcomponent(subcomponent: PsiClass): Collection<PsiClass> {
  val buildersAndFactories = subcomponent.innerClasses.filter {
    it.hasAnnotation(DAGGER_SUBCOMPONENT_BUILDER_ANNOTATION) ||
    it.hasAnnotation(DAGGER_SUBCOMPONENT_FACTORY_ANNOTATION)
  }.map { it.qualifiedName }

  val components = getClassesWithAnnotation(subcomponent.project, DAGGER_COMPONENT_ANNOTATION, subcomponent.useScope)
  val componentsMethods = components.filter { component ->
    component.methods.any {
      val returnClazz = (it.returnType as? PsiClassType)?.resolve() ?: return@any false
      returnClazz.qualifiedName == subcomponent.qualifiedName || returnClazz.qualifiedName in buildersAndFactories
    }
  }

  val modules = getDaggerModulesForSubcomponent(subcomponent)

  val componentsThroughModules = modules.flatMap { module ->
    getClassesWithAnnotation(module.project, DAGGER_COMPONENT_ANNOTATION, module.useScope).filter { component ->
      component.getAnnotation(DAGGER_COMPONENT_ANNOTATION)
        ?.isClassPresentedInAttribute(MODULES_ATTR_NAME, module.qualifiedName!!) == true
    }
  }

  return (componentsMethods + componentsThroughModules).distinct()
}

/**
 * Returns subcomponents of a [component].
 *
 * A subcomponents can be associated with a [component] in two ways:
 * 1. Component's methods that return the class annotated [DAGGER_SUBCOMPONENT_ANNOTATION] or [DAGGER_SUBCOMPONENT_BUILDER_ANNOTATION] or
 * [DAGGER_SUBCOMPONENT_FACTORY_ANNOTATION]
 * 2. Classes from [SUBCOMPONENTS_ATTR_NAME] attribute of a @Module that a [component] installs.
 *
 * See [Dagger doc](https://dagger.dev/subcomponents.html).
 */
internal fun getSubcomponents(component: PsiClass): Collection<PsiClass> {
  val modules = component.getAnnotation(DAGGER_COMPONENT_ANNOTATION)?.getClassesFromAttribute(MODULES_ATTR_NAME)
  val subcomponentFromModules = modules?.flatMap {
    it.getAnnotation(DAGGER_MODULE_ANNOTATION)?.getClassesFromAttribute(SUBCOMPONENTS_ATTR_NAME) ?: emptyList()
  } ?: emptyList()

  val subcomponentFromMethods: Collection<PsiClass> = component.methods.mapNotNull {
    val returnClazz = (it.returnType as? PsiClassType)?.resolve() ?: return@mapNotNull null

    if (returnClazz.hasAnnotation(DAGGER_SUBCOMPONENT_ANNOTATION) ||
        returnClazz.hasAnnotation(DAGGER_SUBCOMPONENT_BUILDER_ANNOTATION) ||
        returnClazz.hasAnnotation(DAGGER_SUBCOMPONENT_FACTORY_ANNOTATION)
    ) {
      returnClazz
    }
    else {
      null
    }
  }

  return subcomponentFromModules + subcomponentFromMethods
}

/**
 * Returns classes annotated [DAGGER_MODULE_ANNOTATION] that in [SUBCOMPONENTS_ATTR_NAME] attribute have subcomponents class.
 */
private fun getDaggerModulesForSubcomponent(subcomponent: PsiClass): Collection<PsiClass> {
  val modules = getClassesWithAnnotation(subcomponent.project, DAGGER_MODULE_ANNOTATION, subcomponent.useScope)
  return modules.filter {
    it.getAnnotation(DAGGER_MODULE_ANNOTATION)?.isClassPresentedInAttribute(SUBCOMPONENTS_ATTR_NAME, subcomponent.qualifiedName!!) == true
  }
}

/**
 * Returns true if an attribute with an [attrName] contains class with fully qualified name equals [fqcn].
 *
 * Assumes that the attribute has type Class<?>[]`.
 */
private fun PsiAnnotation.isClassPresentedInAttribute(attrName: String, fqcn: String): Boolean {
  val attr = findAttributeValue(attrName) as? PsiArrayInitializerMemberValue ?: return false
  val classes = attr.initializers
  return classes.any {
    val clazz = ((it as? PsiClassObjectAccessExpression)?.operand?.type as? PsiClassType)?.resolve()
    clazz?.qualifiedName == fqcn
  }
}

/**
 * Returns Dagger-components and Dagger-modules that in a attribute “modules” and "includes" respectively have a [module] class.
 *
 * Dagger-component is interface annotated [DAGGER_COMPONENT_ANNOTATION] or [DAGGER_SUBCOMPONENT_ANNOTATION]).
 * Dagger-module is class annotated [DAGGER_MODULE_ANNOTATION].
 * The "modules" attribute and "includes" have a type `Class<?>[]`.
 */
fun getUsagesForDaggerModule(module: PsiClass): Collection<PsiClass> {
  val componentQuery = getClassesWithAnnotation(module.project, DAGGER_COMPONENT_ANNOTATION, module.useScope)
  val subComponentQuery = getClassesWithAnnotation(module.project, DAGGER_SUBCOMPONENT_ANNOTATION, module.useScope)
  val moduleQuery = getClassesWithAnnotation(module.project, DAGGER_MODULE_ANNOTATION, module.useScope)
  val predicate: (PsiClass, String, String) -> Boolean = predicate@{ component, annotationName, attrName ->
    component.getAnnotation(annotationName)?.isClassPresentedInAttribute(attrName, module.qualifiedName!!) == true
  }
  return componentQuery.filter { predicate(it, DAGGER_COMPONENT_ANNOTATION, MODULES_ATTR_NAME) } +
         subComponentQuery.filter { predicate(it, DAGGER_SUBCOMPONENT_ANNOTATION, MODULES_ATTR_NAME) } +
         moduleQuery.filter { predicate(it, DAGGER_MODULE_ANNOTATION, INCLUDES_ATTR_NAME) }
}

/**
 * Return classes annotated [DAGGER_COMPONENT_ANNOTATION] that in a attribute [DEPENDENCIES_ATTR_NAME] have a [component] class.
 */
fun getDependantComponentsForComponent(component: PsiClass): Collection<PsiClass> {
  val components = getClassesWithAnnotation(component.project, DAGGER_COMPONENT_ANNOTATION, component.useScope)
  return components.filter {
    it.getAnnotation(DAGGER_COMPONENT_ANNOTATION)?.isClassPresentedInAttribute(DEPENDENCIES_ATTR_NAME, component.qualifiedName!!) == true
  }
}

private fun PsiAnnotation.getClassesFromAttribute(attrName: String): Collection<PsiClass> {
  val attr = findAttributeValue(attrName) as? PsiArrayInitializerMemberValue ?: return emptyList()
  val classes = attr.initializers
  return classes.mapNotNull { ((it as? PsiClassObjectAccessExpression)?.operand?.type as? PsiClassType)?.resolve() }
}

/**
 * Tries to cast PsiElement to PsiClass.
 */
internal fun PsiElement.toPsiClass(): PsiClass? = when {
  this is PsiClass -> this
  this is KtClass && this !is KtEnumEntry -> this.toLightClass()
  this is KtObjectDeclaration -> this.toLightClass()
  else -> error("[Dagger editor] Unable to cast ${this.javaClass} to PsiClass")
}
