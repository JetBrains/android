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
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexClassWrapper
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import java.io.DataInput
import java.io.DataOutput
import java.util.EnumMap
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.psi.KtClass

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
        IndexValue.DataType.COMPONENT_WITH_MODULE to DaggerAnnotations.COMPONENT,
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY to DaggerAnnotations.COMPONENT,
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE to DaggerAnnotations.SUBCOMPONENT,
        IndexValue.DataType.MODULE_WITH_INCLUDE to DaggerAnnotations.MODULE,
        IndexValue.DataType.MODULE_WITH_SUBCOMPONENT to DaggerAnnotations.MODULE,
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
      wrapper.getAnnotationsByName(annotationName).flatMap { annotation ->
        annotation.getArgumentClassNames(annotationArgumentName)
      }
    for (className in listedClasses) {
      val indexValue = ClassIndexValue(dataType, wrapper.getFqName())
      val classSimpleName =
        className.substringAfterLast('.', /* missingDelimiterValue = */ className)
      indexEntries.addIndexValue(classSimpleName, indexValue)
    }
  }
}

@VisibleForTesting
internal data class ClassIndexValue(
  override val dataType: DataType,
  private val classFqName: String
) : IndexValue() {

  override fun save(output: DataOutput) {
    output.writeString(classFqName)
  }

  class Reader
  private constructor(override val supportedType: DataType, val factory: (String) -> IndexValue) :
    IndexValue.Reader {
    override fun read(input: DataInput) = factory.invoke(input.readString())

    companion object {
      val ComponentWithModule =
        Reader(DataType.COMPONENT_WITH_MODULE) { classFqName ->
          ClassIndexValue(DataType.COMPONENT_WITH_MODULE, classFqName)
        }
      val ComponentWithDependency =
        Reader(DataType.COMPONENT_WITH_DEPENDENCY) { classFqName ->
          ClassIndexValue(DataType.COMPONENT_WITH_DEPENDENCY, classFqName)
        }
      val SubcomponentWithModule =
        Reader(DataType.SUBCOMPONENT_WITH_MODULE) { classFqName ->
          ClassIndexValue(DataType.SUBCOMPONENT_WITH_MODULE, classFqName)
        }
      val ModuleWithInclude =
        Reader(DataType.MODULE_WITH_INCLUDE) { classFqName ->
          ClassIndexValue(DataType.MODULE_WITH_INCLUDE, classFqName)
        }
      val ModuleWithSubcomponent =
        Reader(DataType.MODULE_WITH_SUBCOMPONENT) { classFqName ->
          ClassIndexValue(DataType.MODULE_WITH_SUBCOMPONENT, classFqName)
        }
    }
  }

  companion object {
    private val identifyClassKotlin =
      DaggerElementIdentifier<KtClass> {
        when {
          it.hasAnnotation(DaggerAnnotations.COMPONENT) -> ComponentDaggerElement(it)
          it.hasAnnotation(DaggerAnnotations.SUBCOMPONENT) -> SubcomponentDaggerElement(it)
          it.hasAnnotation(DaggerAnnotations.MODULE) -> ModuleDaggerElement(it)
          else -> null
        }
      }

    private val identifyClassJava =
      DaggerElementIdentifier<PsiClass> {
        when {
          it.hasAnnotation(DaggerAnnotations.COMPONENT) -> ComponentDaggerElement(it)
          it.hasAnnotation(DaggerAnnotations.SUBCOMPONENT) -> SubcomponentDaggerElement(it)
          it.hasAnnotation(DaggerAnnotations.MODULE) -> ModuleDaggerElement(it)
          else -> null
        }
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktClassIdentifiers = listOf(identifyClassKotlin),
        psiClassIdentifiers = listOf(identifyClassJava),
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    return JavaPsiFacade.getInstance(project).findClass(classFqName, scope)?.let { listOf(it) }
      ?: emptyList()
  }

  override fun getMatchingIndexKeyPsiTypes(resolveCandidate: PsiElement): Set<PsiType> {
    // The resolve candidate is something like the `CoffeeShop` class, and the related type would be
    // `DripCoffeeModule`:
    //   @Component(modules = DripCoffeeModule.class)
    //   interface CoffeeShop {}
    // This method looks on the candidate for the appropriate annotation and annotation argument,
    // and then gets the PsiTypes corresponding to the classes listed in that argument.
    val annotationArgument =
      (resolveCandidate as? PsiClass)
        ?.getAnnotation(annotationsByDataType[dataType]!!)
        ?.findAttributeValue(annotationArgumentsByDataType[dataType]!!)
        ?: return emptySet()

    // In Java, the annotation's array argument may be specified without the array syntax if there's
    // only a single value. Look for both variations. (In Kotlin, the list form is always used.)
    return when (annotationArgument) {
      is PsiClassObjectAccessExpression -> setOf(annotationArgument.operand.type)
      is PsiArrayInitializerMemberValue ->
        annotationArgument.initializers
          .filterIsInstance<PsiClassObjectAccessExpression>()
          .map { it.operand.type }
          .toSet()
      else -> return emptySet()
    }
  }

  override val daggerElementIdentifiers = identifiers
}

internal class ModuleDaggerElement(psiElement: PsiElement) :
  DaggerElement(psiElement, Type.MODULE) {
  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    val fromIndex =
      getRelatedDaggerElementsFromIndex(setOf(Type.COMPONENT, Type.MODULE, Type.SUBCOMPONENT))
        .groupBy { it.daggerType }
        .withDefault { emptyList() }

    return fromIndex.getValue(Type.COMPONENT).map {
      DaggerRelatedElement(it, DaggerBundle.message("included.in.components"))
    } +
      fromIndex.getValue(Type.SUBCOMPONENT).map {
        DaggerRelatedElement(it, DaggerBundle.message("included.in.subcomponents"))
      } +
      fromIndex.getValue(Type.MODULE).map {
        DaggerRelatedElement(it, DaggerBundle.message("included.in.modules"))
      }
  }
}

internal abstract class ComponentDaggerElementBase(psiElement: PsiElement, daggerType: Type) :
  DaggerElement(psiElement, daggerType) {
  protected abstract val definingAnnotationName: String

  protected fun getIncludedModulesAndSubcomponents(): List<DaggerRelatedElement> {
    val moduleClasses =
      getRelatedDaggerElementsFromAnnotation(
        psiElement,
        definingAnnotationName,
        "modules",
        DaggerAnnotations.MODULE
      )
    val subcomponentClasses =
      moduleClasses.flatMap {
        getRelatedDaggerElementsFromAnnotation(
          it,
          DaggerAnnotations.MODULE,
          "subcomponents",
          DaggerAnnotations.SUBCOMPONENT
        )
      }

    val moduleElements =
      moduleClasses.map {
        DaggerRelatedElement(ModuleDaggerElement(it), DaggerBundle.message("modules.included"))
      }
    val subcomponentElements =
      subcomponentClasses.map {
        DaggerRelatedElement(SubcomponentDaggerElement(it), DaggerBundle.message("subcomponents"))
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
      annotationName: String,
      annotationArgumentName: String,
      requiredAnnotationNameOnTarget: String
    ): List<PsiClass> {
      val psiClass =
        when (psiElement) {
          is PsiClass -> psiElement
          is KtClass -> psiElement.toLightClass()
          else -> null
        }

      val attributeValue =
        psiClass?.getAnnotation(annotationName)?.findAttributeValue(annotationArgumentName)
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
        referencedClass?.takeIf { c -> c.hasAnnotation(requiredAnnotationNameOnTarget) }
      }
    }
  }
}

internal class ComponentDaggerElement(psiElement: PsiElement) :
  ComponentDaggerElementBase(psiElement, Type.COMPONENT) {
  override val definingAnnotationName = DaggerAnnotations.COMPONENT

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    val elementsFromIndex =
      getRelatedDaggerElementsFromIndex(setOf(Type.COMPONENT)).map {
        DaggerRelatedElement(it, DaggerBundle.message("parent.components"))
      }
    return elementsFromIndex + getIncludedModulesAndSubcomponents()
  }
}

internal class SubcomponentDaggerElement(psiElement: PsiElement) :
  ComponentDaggerElementBase(psiElement, Type.SUBCOMPONENT) {
  override val definingAnnotationName = DaggerAnnotations.SUBCOMPONENT

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    // Containing [sub]components are two levels up the graph. Look up the containing modules in
    // the index, and then the containing [sub]components from there. Only the parent components
    // and subcomponents are returned; the intermediate modules are not.
    val containingComponents =
      getRelatedDaggerElementsFromIndex(setOf(Type.MODULE))
        .flatMap { it.getRelatedDaggerElementsFromIndex(setOf(Type.COMPONENT, Type.SUBCOMPONENT)) }
        .map { DaggerRelatedElement(it, DaggerBundle.message("parent.components")) }

    return containingComponents + getIncludedModulesAndSubcomponents()
  }
}
