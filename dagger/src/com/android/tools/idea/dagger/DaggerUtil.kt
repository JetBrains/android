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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.AndroidPsiUtils.toPsiType
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.util.module
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
const val DAGGER_LAZY = "dagger.Lazy"
const val DAGGER_BINDS_INSTANCE_ANNOTATION = "dagger.BindsInstance"
const val INJECT_ANNOTATION = "javax.inject.Inject"
const val DAGGER_COMPONENT_ANNOTATION = "dagger.Component"
const val DAGGER_SUBCOMPONENT_ANNOTATION = "dagger.Subcomponent"
const val DAGGER_SUBCOMPONENT_FACTORY_ANNOTATION = "dagger.Subcomponent.Factory"
const val DAGGER_ENTRY_POINT_ANNOTATION = "dagger.hilt.EntryPoint"
const val DAGGER_VIEW_MODEL_INJECT_ANNOTATION = "androidx.hilt.lifecycle.ViewModelInject"
const val DAGGER_WORKER_INJECT_ANNOTATION = "androidx.hilt.work.WorkerInject"
const val JAVAX_INJECT_PROVIDER = "javax.inject.Provider"
const val DAGGER_ASSISTED_FACTORY = "dagger.assisted.AssistedFactory"
const val DAGGER_ASSISTED_INJECT = "dagger.assisted.AssistedInject"
const val DAGGER_ASSISTED = "dagger.assisted.Assisted"

private const val INCLUDES_ATTR_NAME = "includes"
private const val MODULES_ATTR_NAME = "modules"
private const val DEPENDENCIES_ATTR_NAME = "dependencies"
private const val SUBCOMPONENTS_ATTR_NAME = "subcomponents"

private val injectedConstructorAnnotations = setOf(INJECT_ANNOTATION, DAGGER_VIEW_MODEL_INJECT_ANNOTATION, DAGGER_WORKER_INJECT_ANNOTATION)

/**
 * Returns all classes annotated [annotationName] in a given [searchScope].
 */
private fun getClassesWithAnnotation(project: Project, annotationName: String, searchScope: SearchScope) =
  DaggerAnnotatedElementsSearch.getInstance(project).searchClasses(annotationName, searchScope)

/**
 * Returns all Dagger providers (see [isDaggerProvider] for a [type] with a [qualifierInfo] within a [scope].
 *
 * Null [qualifierInfo] means that binding has not qualifier or has more then one.
 */
private fun getDaggerProviders(type: PsiType, qualifierInfo: QualifierInfo?, scope: GlobalSearchScope): Collection<PsiModifierListOwner> {
  return getDaggerProvidesMethodsForType(type, scope).filterByQualifier(qualifierInfo) +
         getDaggerBindsMethodsForType(type, scope).filterByQualifier(qualifierInfo) +
         getDaggerBindsInstanceMethodsAndParametersForType(type, scope).filterByQualifier(qualifierInfo) +
         getDaggerInjectedConstructorsForType(type) +
         getAssistedFactoryObjectForType(type)
}

private fun getAssistedFactoryObjectForType(type: PsiType): Collection<PsiModifierListOwner> {
  val clazz = (type as? PsiClassType)?.resolve() ?: return emptyList()
  return if (clazz.isDaggerAssistedFactory) listOf(clazz) else emptyList()
}

/**
 * Returns all @BindsInstance-annotated methods and params that return given [type] within [scope].
 */
private fun getDaggerBindsInstanceMethodsAndParametersForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiModifierListOwner> {
  val unboxedType = type.unboxed
  val project = scope.project ?: return emptyList()
  val search = DaggerAnnotatedElementsSearch.getInstance(project)
  return search.searchMethods(DAGGER_BINDS_INSTANCE_ANNOTATION, scope, unboxedType) +
         search.searchParameters(DAGGER_BINDS_INSTANCE_ANNOTATION, scope, unboxedType)
}

/**
 * Returns all Dagger providers (@Provide/@Binds-annotated methods, @Inject-annotated constructors) for [element].
 */
@WorkerThread
fun getDaggerProvidersFor(element: PsiElement): Collection<PsiModifierListOwner> {
  val module = element.module ?: return emptyList()
  val scope = module.moduleWithDependentsAndDependenciesScope()
  val (type, qualifierInfo) = extractTypeAndQualifierInfo(element) ?: return emptyList()

  return getDaggerProviders(type, qualifierInfo, scope)
}

/**
 * Returns all @Inject-annotated fields of [type] within given [scope].
 */
private fun getInjectedFieldsForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiField> {
  val project = scope.project ?: return emptyList()
  return DaggerAnnotatedElementsSearch.getInstance(project).searchFields(INJECT_ANNOTATION, scope, type.unboxed)
}

/**
 * Returns params of @Provides/@Binds/@Inject-annotated method or @Inject-annotated constructor that have given [type] within given [scope].
 */
private fun getParamsOfDaggerProvidersForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiParameter> {
  val unboxedType = type.unboxed
  val project = scope.project ?: return emptyList()
  val search = DaggerAnnotatedElementsSearch.getInstance(project)
  return injectedConstructorAnnotations.flatMap { search.searchParameterOfMethodAnnotatedWith(it, scope, unboxedType) } +
         search.searchParameterOfMethodAnnotatedWith(DAGGER_BINDS_ANNOTATION, scope, unboxedType) +
         search.searchParameterOfMethodAnnotatedWith(DAGGER_PROVIDES_ANNOTATION, scope, unboxedType) +
         search.searchParameterOfMethodAnnotatedWith(DAGGER_ASSISTED_INJECT, scope, unboxedType)
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
@WorkerThread
fun getDaggerConsumersFor(element: PsiElement): Collection<PsiVariable> {
  val module = element.module ?: return emptyList()
  val scope = module.moduleWithDependentsAndDependenciesScope()
  val (type, qualifierInfo) = extractTypeAndQualifierInfo(element) ?: return emptyList()

  var consumers = getDaggerConsumers(type, qualifierInfo, scope)

  val lazyClass = JavaPsiFacade.getInstance(element.project).findClass(DAGGER_LAZY, scope)
  if (lazyClass != null) {
    val lazyType = PsiElementFactory.getInstance(element.project).createType(lazyClass, type)
    consumers += getDaggerConsumers(lazyType, qualifierInfo, scope)
  }

  val javaxInjectProviderClass = JavaPsiFacade.getInstance(element.project).findClass(JAVAX_INJECT_PROVIDER, scope)
  if (javaxInjectProviderClass != null) {
    val javaxInjectProviderType = PsiElementFactory.getInstance(element.project).createType(javaxInjectProviderClass, type)
    consumers += getDaggerConsumers(javaxInjectProviderType, qualifierInfo, scope)
  }

  return consumers
}

/**
 * Returns all the relevant methods from @AssistedFactory-annotated classes where the return type matches class [constructor] belongs to.
 */
@WorkerThread
fun getDaggerAssistedFactoryMethodsForAssistedInjectedConstructor(constructor: PsiElement): Collection<PsiMethod> {
  val module = constructor.module ?: return emptyList()
  val (type, _) = extractTypeAndQualifierInfo(constructor) ?: return emptyList()

  // No need to check for qualifiers since AssistedFactories can only have a single abstract, non-default method.
  return getAssistedFactoryMethodsForType(type, module)
}

/**
 * Returns all the relevant methods from @AssistedFactory-annotated clases where the return type matches [type], scope to the [module] and
 * it's dependents.
 */
private fun getAssistedFactoryMethodsForType(type: PsiType, module: Module): Collection<PsiMethod> {
  val scope = module.moduleWithDependentsAndDependenciesScope()
  val psiClasses = DaggerAnnotatedElementsSearch.getInstance(module.project).searchClasses(DAGGER_ASSISTED_FACTORY, scope)
  return psiClasses.flatMap { it.methods.toList() }.filter { it.returnType == type }
}

/**
 * Returns all the @AssistedInject-annotated constructors for a given @AssistedFactory abstract method
 */
@WorkerThread
fun getDaggerAssistedInjectConstructorForAssistedFactoryMethod(element: PsiElement): Collection<PsiMethod> {
  val (type, _) = extractTypeAndQualifierInfo(element) ?: return emptyList()
  return getDaggerAssistedInjectConstructorsForType(type)
}

/**
 * Returns all @AssistedInject-annotated constructors for a given [type].
 */
private fun getDaggerAssistedInjectConstructorsForType(type: PsiType): Collection<PsiMethod> {
  val clazz = (type as? PsiClassType)?.resolve() ?: return emptyList()
  return clazz.constructors.filter { it.hasAnnotation(DAGGER_ASSISTED_INJECT) }
}

/**
 * Returns all @Inject-annotated constructors for a given [type].
 */
private fun getDaggerInjectedConstructorsForType(type: PsiType): Collection<PsiMethod> {
  val clazz = (type as? PsiClassType)?.resolve() ?: return emptyList()
  return clazz.constructors.filter { constructor -> injectedConstructorAnnotations.any { constructor.hasAnnotation(it) } }
}

/**
 * Returns all @Provide-annotated methods that return given [type] within [scope].
 */
private fun getDaggerProvidesMethodsForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiMethod> {
  val project = scope.project ?: return emptyList()
  return DaggerAnnotatedElementsSearch.getInstance(project).searchMethods(DAGGER_PROVIDES_ANNOTATION, scope, type.unboxed)
}

/**
 * Returns all @Binds-annotated methods that return given [type] within [scope].
 */
private fun getDaggerBindsMethodsForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiMethod> {
  val project = scope.project ?: return emptyList()
  return DaggerAnnotatedElementsSearch.getInstance(project).searchMethods(DAGGER_BINDS_ANNOTATION, scope, type.unboxed)
}

/**
 * True if PsiField has @Inject annotation.
 */
private val PsiField.isInjected get() = hasAnnotation(INJECT_ANNOTATION)

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
  get() = this is PsiMethod && isConstructor && injectedConstructorAnnotations.any { this.hasAnnotation(it) } ||
          this is KtConstructor<*> && injectedConstructorAnnotations.any { this.findAnnotation(FqName(it)) != null }

private val PsiElement?.isBindsInstanceMethodOrParameter: Boolean
  get() {
    return this is PsiMethod && hasAnnotation(DAGGER_BINDS_INSTANCE_ANNOTATION) ||
           this is KtFunction && findAnnotation(FqName(DAGGER_BINDS_INSTANCE_ANNOTATION)) != null ||
           this is PsiParameter && hasAnnotation(DAGGER_BINDS_INSTANCE_ANNOTATION) ||
           this is KtParameter && findAnnotation(FqName(DAGGER_BINDS_INSTANCE_ANNOTATION)) != null
  }

private val PsiElement?.isAssistedParameter: Boolean
  get() {
    return this is PsiParameter && hasAnnotation(DAGGER_ASSISTED) ||
           this is KtParameter && findAnnotation(FqName(DAGGER_ASSISTED)) != null
  }

/**
 * True if PsiElement is Dagger provider i.e @Provides/@Binds/@BindsInstance-annotated method or @Inject-annotated constructor or
 * @BindsInstance-annotated parameter.
 */
val PsiElement?.isDaggerProvider get() = isProvidesMethod || isBindsMethod || isInjectedConstructor || isBindsInstanceMethodOrParameter || isDaggerAssistedFactory

/**
 * True if PsiElement is @AssistedInject-annotated constructor.
 */
val PsiElement?.isAssistedInjectedConstructor: Boolean
  get() = this is PsiMethod && isConstructor && this.hasAnnotation(DAGGER_ASSISTED_INJECT) ||
          this is KtConstructor<*> && this.findAnnotation(FqName(DAGGER_ASSISTED_INJECT)) != null


/**
 * True if PsiElement is @AssistedInject-annotated constructor.
 */
val PsiElement?.isAssistedFactoryMethod: Boolean
  get() = (this is PsiMethod && containingClass.isDaggerAssistedFactory && this.returnType?.unboxed != PsiTypes.voidType()) ||
          (this is KtNamedFunction && containingClass().isDaggerAssistedFactory && this.psiType?.unboxed != PsiTypes.voidType())

/**
 * True if PsiElement is Dagger consumer i.e @Inject-annotated field or param of Dagger provider, see [isDaggerProvider].
 */
val PsiElement?.isDaggerConsumer: Boolean
  get() {
    return this is KtLightField && lightMemberOrigin?.originalElement?.isInjected == true ||
           this is PsiField && isInjected ||
           this is KtProperty && isInjected ||
           this is PsiParameter && declarationScope.isDaggerProvider ||
           this is KtParameter && this.ownerFunction.isDaggerProvider ||
           this is KtParameter && this.ownerFunction.isAssistedInjectedConstructor && !this.isAssistedParameter ||
           this is PsiParameter && declarationScope.isAssistedInjectedConstructor && !this.isAssistedParameter
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

private val PsiMethod.isInstantiationMethod: Boolean get() = parameterList.isEmpty && returnType?.isSubcomponentCreatorType == false

private val KtNamedFunction.isInstantiationMethod get() = valueParameters.isEmpty() && psiType?.isSubcomponentCreatorType == false

private val PsiType.isSubcomponentCreatorType: Boolean
  get() {
    if (this is PsiClassType) {
      val c = resolve() ?: return false
      return c.isDaggerSubcomponent || c.isDaggerSubcomponentFactory
    }
    return false
  }

internal val PsiElement?.isDaggerComponentInstantiationMethod: Boolean
  get() = this is PsiMethod && containingClass.isDaggerComponent && isInstantiationMethod ||
          this is KtNamedFunction && containingClass().isDaggerComponent && isInstantiationMethod ||
          this is KtProperty && containingClass().isDaggerComponent && psiType?.isSubcomponentCreatorType == false

internal val PsiElement?.isDaggerEntryPointInstantiationMethod: Boolean
  get() = this is PsiMethod && containingClass.isDaggerEntryPoint && isInstantiationMethod ||
          this is KtNamedFunction && containingClass().isDaggerEntryPoint && isInstantiationMethod ||
          this is KtProperty && containingClass().isDaggerEntryPoint && psiType?.isSubcomponentCreatorType == false

internal val PsiElement?.isDaggerComponent get() = isClassOrObjectAnnotatedWith(DAGGER_COMPONENT_ANNOTATION)

internal val PsiElement?.isDaggerEntryPoint get() = isClassOrObjectAnnotatedWith(DAGGER_ENTRY_POINT_ANNOTATION)

internal val PsiElement?.isDaggerSubcomponent get() = isClassOrObjectAnnotatedWith(DAGGER_SUBCOMPONENT_ANNOTATION)

internal val PsiElement?.isDaggerSubcomponentFactory get() = isClassOrObjectAnnotatedWith(DAGGER_SUBCOMPONENT_FACTORY_ANNOTATION)

internal val PsiElement?.isDaggerAssistedFactory get() = isClassOrObjectAnnotatedWith(DAGGER_ASSISTED_FACTORY)

/**
 * Returns pair of a type and an optional [QualifierInfo] for [PsiElement].
 *
 * Returns null if it's impossible to extract type.
 */
private fun extractTypeAndQualifierInfo(element: PsiElement): Pair<PsiType, QualifierInfo?>? {
  var type: PsiType =
    when (element) {
      is PsiClass -> toPsiType(element)
      is KtClass -> element.toPsiType()
      is PsiMethod -> if (element.isConstructor) element.containingClass?.let { toPsiType(it) } else element.returnType
      is KtFunction -> if (element is KtConstructor<*>) element.containingClass()?.toPsiType() else element.psiType
      is PsiField -> element.type
      is KtProperty -> element.psiType
      is PsiParameter -> element.type
      is KtParameter -> element.psiType
      else -> null
    } ?: return null

  if (type is PsiClassType &&
      type.resolve()?.let { it.qualifiedName == DAGGER_LAZY || it.qualifiedName == JAVAX_INJECT_PROVIDER } == true) {
    // For dagger.Lazy<Type> or javax.inject.Provider<Type> assigns Type.
    type = type.parameters.firstOrNull() ?: return null
  }

  return Pair(type, element.getQualifierInfo())
}

/**
 * Returns methods of interfaces annotated [DAGGER_COMPONENT_ANNOTATION] that return a type and have a [QualifierInfo] as a [provider].
 */
@WorkerThread
fun getDaggerComponentMethodsForProvider(provider: PsiElement): Collection<PsiMethod> {
  val (type, qualifierInfo) = extractTypeAndQualifierInfo(provider) ?: return emptyList()
  val components = getClassesWithAnnotation(provider.project, DAGGER_COMPONENT_ANNOTATION, provider.useScope)
  return components.flatMap {
    // Instantiating methods doesn't have parameters.
    component ->
    ProgressManager.checkCanceled()
    component.methods.filter { it.returnType?.unboxed == type.unboxed && !it.hasParameters() }.filterByQualifier(qualifierInfo)
  }
}

/**
 * Returns methods of interfaces annotated [DAGGER_ENTRY_POINT_ANNOTATION] that return a type and have a [QualifierInfo] as a [provider].
 */
@WorkerThread
fun getDaggerEntryPointsMethodsForProvider(provider: PsiElement): Collection<PsiMethod> {
  val (type, qualifierInfo) = extractTypeAndQualifierInfo(provider) ?: return emptyList()
  val entryPoints = getClassesWithAnnotation(provider.project, DAGGER_ENTRY_POINT_ANNOTATION, provider.useScope)
  return entryPoints.flatMap {
    // Instantiating methods doesn't have parameters.
    component ->
    ProgressManager.checkCanceled()
    component.methods.filter { it.returnType?.unboxed == type.unboxed && !it.hasParameters() }.filterByQualifier(qualifierInfo)
  }
}

/**
 * Returns interfaces annotated [DAGGER_COMPONENT_ANNOTATION] or [DAGGER_SUBCOMPONENT_ANNOTATION] that are parents to a [subcomponent].
 *
 * A component is a parent of [subcomponent] if the [subcomponent] class is added to the 'subcomponents' attribute of a @Module
 * that the parent component installs.
 *
 * See [Dagger doc](https://dagger.dev/subcomponents.html).
 */
@WorkerThread
internal fun getDaggerParentComponentsForSubcomponent(subcomponent: PsiClass): Collection<PsiClass> {

  val components = getClassesWithAnnotation(subcomponent.project, DAGGER_COMPONENT_ANNOTATION, subcomponent.useScope) +
                   getClassesWithAnnotation(subcomponent.project, DAGGER_SUBCOMPONENT_ANNOTATION, subcomponent.useScope)

  val modulesFQCN = getDaggerModulesForSubcomponent(subcomponent).map { it.qualifiedName }

  return components.filter { component ->
    ProgressManager.checkCanceled()
    getModulesForComponent(component).any { module -> modulesFQCN.contains(module.qualifiedName) }
  }
}

/**
 * Returns subcomponents of a [component].
 *
 * [component] is an interface annotated [DAGGER_COMPONENT_ANNOTATION] or [DAGGER_SUBCOMPONENT_ANNOTATION]
 * Subcomponents are classes in [SUBCOMPONENTS_ATTR_NAME] attribute of a @Module that a [component] installs.
 *
 * See [Dagger doc](https://dagger.dev/subcomponents.html).
 */
@WorkerThread
internal fun getSubcomponents(component: PsiClass): Collection<PsiClass> {
  return getModulesForComponent(component).flatMap {
    it.getAnnotation(DAGGER_MODULE_ANNOTATION)?.getClassesFromAttribute(SUBCOMPONENTS_ATTR_NAME) ?: emptyList()
  }
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
@WorkerThread
private fun PsiAnnotation.isClassPresentedInAttribute(attrName: String, fqcn: String): Boolean {
  ProgressManager.checkCanceled()
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
 * Dagger-component is an interface annotated [DAGGER_COMPONENT_ANNOTATION] or [DAGGER_SUBCOMPONENT_ANNOTATION]).
 * Dagger-module is a class annotated [DAGGER_MODULE_ANNOTATION].
 * The "modules" attribute and "includes" have a type `Class<?>[]`.
 */
@WorkerThread
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
@WorkerThread
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

/**
 * Returns an unboxed type if it's applicable and PsiPrimitiveType.getUnboxedType doesn't return null otherwise returns itself.
 *
 * We need to unbox types before comparison because Dagger considers two types as equal if they are equal after unboxing.
 */
internal val PsiType.unboxed: PsiType
  get() = PsiPrimitiveType.getUnboxedType(this) ?: this

/**
 * Returns PsiClasses from value of "modules" attribute of DAGGER_COMPONENT_ANNOTATION or DAGGER_SUBCOMPONENT_ANNOTATION annotations.
 */
@WorkerThread
internal fun getModulesForComponent(component: PsiClass): Collection<PsiClass> {
  val annotation = component.getAnnotation(DAGGER_COMPONENT_ANNOTATION)
                   ?: component.getAnnotation(DAGGER_SUBCOMPONENT_ANNOTATION)
                   ?: return emptyList()
  return annotation.getClassesFromAttribute(MODULES_ATTR_NAME)
}

/**
 * Returns true if [this] component is parent component of a [component] otherwise returns false.
 *
 * [this] is an interface annotated [DAGGER_COMPONENT_ANNOTATION] or [DAGGER_SUBCOMPONENT_ANNOTATION].
 */
internal fun PsiClass.isParentOf(component: PsiClass): Boolean {
  val modules = getModulesForComponent(this)
  return modules.any { module ->
    val subcomponents = module.getAnnotation(DAGGER_MODULE_ANNOTATION)?.getClassesFromAttribute(SUBCOMPONENTS_ATTR_NAME)
                        ?: return@any false
    subcomponents.any { it.qualifiedName == component.qualifiedName }
  }
}

/**
 * Creates a scope that includes the dependents, content and dependencies of the module.
 */
private fun Module.moduleWithDependentsAndDependenciesScope(): GlobalSearchScope {
  return GlobalSearchScope.moduleWithDependentsScope(this).uniteWith(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(this))
}
