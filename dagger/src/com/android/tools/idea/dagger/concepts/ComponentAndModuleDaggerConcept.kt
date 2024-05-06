/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger.concepts

import com.android.tools.idea.dagger.concepts.ComponentAndModuleDaggerConcept.annotationArgumentsByDataType
import com.android.tools.idea.dagger.concepts.ComponentAndModuleDaggerConcept.annotationsByDataType
import com.android.tools.idea.dagger.index.DaggerConceptIndexer
import com.android.tools.idea.dagger.index.DaggerConceptIndexers
import com.android.tools.idea.dagger.index.IndexEntries
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerAnnotation
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexClassWrapper
import com.android.tools.idea.dagger.index.psiwrappers.hasAnnotation
import com.android.tools.idea.dagger.index.readClassId
import com.android.tools.idea.dagger.index.writeClassId
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import java.io.DataInput
import java.io.DataOutput
import java.util.EnumMap

/**
 * Represents a Component, Subcomponent, or Module in Dagger.
 *
 * Example:
 * ```java
 *   @Component(modules = DripCoffeeModule.class)
 *   interface CoffeeShop {
 *     CoffeeMaker maker();
 *   }
 * ```
 *
 * All three recognized items in this [DaggerConcept] are classes with a corresponding annotation.
 * Components, Subcomponents, and Modules can all reference each other via various arguments on
 * those annotations.
 */
object ComponentAndModuleDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(classIndexers = listOf(ComponentIndexer))
  override val indexValueReaders: List<IndexValue.Reader> =
    listOf(
      ClassIndexValue.Reader.ComponentWithModule,
      ClassIndexValue.Reader.ComponentWithDependency,
      ClassIndexValue.Reader.SubcomponentWithModule,
      ClassIndexValue.Reader.ModuleWithInclude,
      ClassIndexValue.Reader.ModuleWithSubcomponent,
    )
  override val daggerElementIdentifiers: DaggerElementIdentifiers = ClassIndexValue.identifiers

  internal val annotationsByDataType =
    EnumMap(
      mapOf(
        IndexValue.DataType.COMPONENT_WITH_MODULE to DaggerAnnotation.COMPONENT,
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY to DaggerAnnotation.COMPONENT,
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE to DaggerAnnotation.SUBCOMPONENT,
        IndexValue.DataType.MODULE_WITH_INCLUDE to DaggerAnnotation.MODULE,
        IndexValue.DataType.MODULE_WITH_SUBCOMPONENT to DaggerAnnotation.MODULE,
      )
    )

  internal val annotationArgumentsByDataType =
    EnumMap(
      mapOf(
        IndexValue.DataType.COMPONENT_WITH_MODULE to "modules",
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY to "dependencies",
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE to "modules",
        IndexValue.DataType.MODULE_WITH_INCLUDE to "includes",
        IndexValue.DataType.MODULE_WITH_SUBCOMPONENT to "subcomponents",
      )
    )
}

private object ComponentIndexer : DaggerConceptIndexer<DaggerIndexClassWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexClassWrapper, indexEntries: IndexEntries) {
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.COMPONENT_WITH_MODULE, indexEntries)
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, indexEntries)
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.SUBCOMPONENT_WITH_MODULE, indexEntries)
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.MODULE_WITH_INCLUDE, indexEntries)
    lookForClassesOnAnnotation(wrapper, IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, indexEntries)
  }

  private fun lookForClassesOnAnnotation(
    wrapper: DaggerIndexClassWrapper,
    dataType: IndexValue.DataType,
    indexEntries: IndexEntries
  ) {
    val annotationName = annotationsByDataType[dataType]!!
    val annotationArgumentName = annotationArgumentsByDataType[dataType]!!
    val listedClasses =
      wrapper.getAnnotations(annotationName).flatMap { annotation ->
        annotation.getArgumentClassNames(annotationArgumentName)
      }
    for (className in listedClasses) {
      val indexValue = ClassIndexValue(dataType, wrapper.getClassId())
      val classSimpleName =
        className.substringAfterLast('.', /* missingDelimiterValue = */ className)
      indexEntries.addIndexValue(classSimpleName, indexValue)
    }
  }
}

@VisibleForTesting
internal data class ClassIndexValue(
  override val dataType: DataType,
  private val classId: ClassId,
) : IndexValue() {

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
  }

  class Reader
  private constructor(override val supportedType: DataType, val factory: (ClassId) -> IndexValue) :
    IndexValue.Reader {
    override fun read(input: DataInput) = factory.invoke(input.readClassId())

    companion object {
      val ComponentWithModule =
        Reader(DataType.COMPONENT_WITH_MODULE) { classId ->
          ClassIndexValue(DataType.COMPONENT_WITH_MODULE, classId)
        }
      val ComponentWithDependency =
        Reader(DataType.COMPONENT_WITH_DEPENDENCY) { classId ->
          ClassIndexValue(DataType.COMPONENT_WITH_DEPENDENCY, classId)
        }
      val SubcomponentWithModule =
        Reader(DataType.SUBCOMPONENT_WITH_MODULE) { classId ->
          ClassIndexValue(DataType.SUBCOMPONENT_WITH_MODULE, classId)
        }
      val ModuleWithInclude =
        Reader(DataType.MODULE_WITH_INCLUDE) { classId ->
          ClassIndexValue(DataType.MODULE_WITH_INCLUDE, classId)
        }
      val ModuleWithSubcomponent =
        Reader(DataType.MODULE_WITH_SUBCOMPONENT) { classId ->
          ClassIndexValue(DataType.MODULE_WITH_SUBCOMPONENT, classId)
        }
    }
  }

  companion object {
    private fun identify(psiElement: KtClassOrObject): DaggerElement? =
      when {
        psiElement is KtEnumEntry -> null
        (psiElement as? KtClass)?.isEnum() == true -> null
        psiElement.hasAnnotation(DaggerAnnotation.COMPONENT) -> ComponentDaggerElement(psiElement)
        psiElement.hasAnnotation(DaggerAnnotation.SUBCOMPONENT) ->
          SubcomponentDaggerElement(psiElement)
        psiElement.hasAnnotation(DaggerAnnotation.MODULE) -> ModuleDaggerElement(psiElement)
        else -> null
      }

    private fun identify(psiElement: PsiClass): DaggerElement? =
      when {
        psiElement.isEnum -> null
        psiElement.hasAnnotation(DaggerAnnotation.COMPONENT) -> ComponentDaggerElement(psiElement)
        psiElement.hasAnnotation(DaggerAnnotation.SUBCOMPONENT) ->
          SubcomponentDaggerElement(psiElement)
        psiElement.hasAnnotation(DaggerAnnotation.MODULE) -> ModuleDaggerElement(psiElement)
        else -> null
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktClassIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
        psiClassIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> =
    listOfNotNull(JavaPsiFacade.getInstance(project).findClass(classId.asFqNameString(), scope))

  override val daggerElementIdentifiers = identifiers
}

internal sealed class ClassDaggerElement : DaggerElement() {

  val classPsiType: PsiType
    get() = psiElement.classToPsiType()

  /**
   * Given a related element, returns the annotation and annotation argument name that would be used
   * to identify that relation. This applies only to relations that are stored in the index.
   *
   * For example, a component includes modules as follows: `@dagger.Component(modules =
   * DripCoffeeModule.class)` If this [ClassDaggerElement] represents the DripCoffeeModule element
   * and the given element is a [ComponentDaggerElement] related type, then this method should
   * return ("dagger.Component", "modules").
   */
  protected abstract fun getRelatedAnnotationForRelatedIndexElement(
    relatedType: DaggerElement
  ): Pair<DaggerAnnotation, String>?

  override fun filterResolveCandidate(resolveCandidate: DaggerElement): Boolean {
    // As an example, the resolve candidate is a DaggerElement pointing to the `CoffeeShop` class,
    // and `this` is a DaggerElement pointing to the `DripCoffeeModule` class.`
    //
    // This method will look at the `CoffeeShop` definition to see if it points to the
    // `DripCoffeeModule` class via an annotation:
    //
    //   @Component(modules = DripCoffeeModule.class)
    //   interface CoffeeShop {}
    val (annotation, argumentName) =
      getRelatedAnnotationForRelatedIndexElement(resolveCandidate) ?: return false
    val resolveCandidateClassElement =
      when (val element = resolveCandidate.psiElement) {
        is PsiClass -> element
        is KtClassOrObject -> element.toLightClass()
        else -> null
      } ?: return false
    val annotationArgument =
      resolveCandidateClassElement
        .getAnnotation(annotation.fqNameString)
        ?.findAttributeValue(argumentName) ?: return false

    // In Java, the annotation's array argument may be specified without the array syntax if there's
    // only a single value. Look for both variations. (In Kotlin, the list form is always used.)
    val referencedTypes =
      when (annotationArgument) {
        is PsiClassObjectAccessExpression -> setOf(annotationArgument.operand.type)
        is PsiArrayInitializerMemberValue ->
          annotationArgument.initializers
            .filterIsInstance<PsiClassObjectAccessExpression>()
            .map { it.operand.type }
            .toSet()
        else -> return false
      }

    return classPsiType in referencedTypes
  }
}

internal data class ModuleDaggerElement(override val psiElement: PsiElement) :
  ClassDaggerElement() {

  override val metricsElementType = DaggerEditorEvent.ElementType.MODULE

  override fun getRelatedAnnotationForRelatedIndexElement(
    relatedType: DaggerElement
  ): Pair<DaggerAnnotation, String>? =
    when (relatedType) {
      is ComponentDaggerElement -> DaggerAnnotation.COMPONENT to "modules"
      is ModuleDaggerElement -> DaggerAnnotation.MODULE to "includes"
      is SubcomponentDaggerElement -> DaggerAnnotation.SUBCOMPONENT to "modules"
      else -> null
    }

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    val fromIndex =
      getRelatedDaggerElementsFromIndex(
        setOf(
          ComponentDaggerElement::class,
          ModuleDaggerElement::class,
          SubcomponentDaggerElement::class
        ),
        classPsiType.getIndexKeys()
      )

    return fromIndex.filterIsInstance<ComponentDaggerElement>().map {
      DaggerRelatedElement(
        it,
        DaggerBundle.message("included.in.components"),
        "navigate.to.component.that.include"
      )
    } +
      fromIndex.filterIsInstance<SubcomponentDaggerElement>().map {
        DaggerRelatedElement(
          it,
          DaggerBundle.message("included.in.subcomponents"),
          "navigate.to.subcomponent.that.include"
        )
      } +
      fromIndex.filterIsInstance<ModuleDaggerElement>().map {
        DaggerRelatedElement(
          it,
          DaggerBundle.message("included.in.modules"),
          "navigate.to.module.that.include"
        )
      }
  }
}

internal sealed class ComponentDaggerElementBase : ClassDaggerElement() {

  protected abstract val definingAnnotation: DaggerAnnotation

  @VisibleForTesting
  internal fun getIncludedModulesAndSubcomponents(): List<DaggerRelatedElement> {
    val moduleClasses =
      getRelatedDaggerElementsFromAnnotation(
        psiElement,
        definingAnnotation,
        "modules",
        DaggerAnnotation.MODULE
      )
    val subcomponentClasses =
      moduleClasses.flatMap {
        getRelatedDaggerElementsFromAnnotation(
          it,
          DaggerAnnotation.MODULE,
          "subcomponents",
          DaggerAnnotation.SUBCOMPONENT
        )
      }

    val moduleElements =
      moduleClasses.map {
        DaggerRelatedElement(
          ModuleDaggerElement(it.kotlinOriginOrSelf),
          DaggerBundle.message("modules.included"),
          "navigate.to.included.module"
        )
      }
    val subcomponentElements =
      subcomponentClasses.map {
        DaggerRelatedElement(
          SubcomponentDaggerElement(it.kotlinOriginOrSelf),
          DaggerBundle.message("subcomponents"),
          "navigate.to.subcomponent"
        )
      }

    return moduleElements + subcomponentElements
  }

  companion object {
    /**
     * Gets a list of classes referenced in an annotation on the given @param[psiElement].
     *
     * Given an annotation of the form:
     *
     * @AnnotationName(argumentName = [ReferencedClass1::class, ReferencedClass2::class])
     *
     * This method returns references to `ReferencedClass1` and `ReferencedClass2`, if those classes
     * themselves contain the annotation specified with @param[requiredAnnotationNameOnTarget].
     */
    private fun getRelatedDaggerElementsFromAnnotation(
      psiElement: PsiElement,
      annotation: DaggerAnnotation,
      annotationArgumentName: String,
      requiredAnnotationOnTarget: DaggerAnnotation
    ): List<PsiClass> {
      val psiClass =
        when (psiElement) {
          is PsiClass -> psiElement
          is KtClassOrObject -> psiElement.toLightClass()
          else -> null
        }

      val attributeValue =
        psiClass?.getAnnotation(annotation.fqNameString)?.findAttributeValue(annotationArgumentName)
          ?: return emptyList()
      val referencedClassExpressions =
        when (attributeValue) {
          is PsiClassObjectAccessExpression -> listOf(attributeValue)
          is PsiArrayInitializerMemberValue ->
            attributeValue.initializers.mapNotNull { it as? PsiClassObjectAccessExpression }
          else -> return emptyList()
        }

      return referencedClassExpressions.mapNotNull {
        val referencedClass = (it.operand.type as? PsiClassType)?.resolve()
        referencedClass?.takeIf { c -> c.hasAnnotation(requiredAnnotationOnTarget) }
      }
    }
  }
}

internal data class ComponentDaggerElement(override val psiElement: PsiElement) :
  ComponentDaggerElementBase() {

  override val metricsElementType = DaggerEditorEvent.ElementType.COMPONENT

  override val definingAnnotation = DaggerAnnotation.COMPONENT

  override fun getRelatedAnnotationForRelatedIndexElement(
    relatedType: DaggerElement
  ): Pair<DaggerAnnotation, String>? =
    when (relatedType) {
      is ComponentDaggerElement -> DaggerAnnotation.COMPONENT to "dependencies"
      else -> null
    }

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    val elementsFromIndex =
      getRelatedDaggerElementsFromIndex<ComponentDaggerElement>(classPsiType.getIndexKeys()).map {
        DaggerRelatedElement(
          it,
          DaggerBundle.message("parent.components"),
          "navigate.to.parent.component"
        )
      }
    return elementsFromIndex + getIncludedModulesAndSubcomponents()
  }
}

internal data class SubcomponentDaggerElement(override val psiElement: PsiElement) :
  ComponentDaggerElementBase() {

  override val metricsElementType = DaggerEditorEvent.ElementType.SUBCOMPONENT

  override val definingAnnotation = DaggerAnnotation.SUBCOMPONENT

  override fun getRelatedAnnotationForRelatedIndexElement(
    relatedType: DaggerElement
  ): Pair<DaggerAnnotation, String>? =
    when (relatedType) {
      is ModuleDaggerElement -> DaggerAnnotation.MODULE to "subcomponents"
      else -> null
    }

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    // Containing [sub]components are two levels up the graph. Look up the containing modules in
    // the index, and then the containing [sub]components from there. Only the parent components
    // and subcomponents are returned; the intermediate modules are not.
    val containingComponents =
      getRelatedDaggerElementsFromIndex<ModuleDaggerElement>(classPsiType.getIndexKeys())
        .flatMap {
          it.getRelatedDaggerElementsFromIndex<ComponentDaggerElementBase>(
            it.classPsiType.getIndexKeys()
          )
        }
        .map {
          DaggerRelatedElement(
            it,
            DaggerBundle.message("parent.components"),
            "navigate.to.parent.component"
          )
        }

    return containingComponents + getIncludedModulesAndSubcomponents()
  }
}
