/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.analytics

import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.DataBindingEvent
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingPollMetadata.LiveDataMetrics
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingPollMetadata.ObservableMetrics
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JavaProjectRootsUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType


private val OBSERVABLE_PRIMITIVES = setOf(
  "ObservableBoolean",
  "ObservableByte",
  "ObservableChar",
  "ObservableField",
  "ObservableShort",
  "ObservableInt",
  "ObservableLong",
  "ObservableFloat",
  "ObservableDouble",
  "ObservableParcelable")

private val OBSERVABLE_COLLECTIONS = setOf(
  "ObservableArrayList",
  "ObservableArrayMap")

private val LIVE_DATA_OBJECTS = setOf(
  "LiveData",
  "MutableLiveData",
  "MediatorLiveData"
)


/**
 * Scans all java and kotlin source files to collect observable data metrics, as defined in the proto [ObservableMetrics].
 */
fun trackObservables(project: Project): ObservableMetrics {
  var primitiveCount = 0
  var collectionCount = 0
  var objectCount = 0
  val observableCountingProcessor = PsiElementProcessor<PsiElement> { psiElement ->
    when {
      isObservablePrimitive(psiElement) -> primitiveCount ++
      isObservableCollection(psiElement) -> collectionCount ++
      isObservableObject(psiElement) -> objectCount ++
    }
    true
  }
  FileTypeIndex.getFiles(
    KotlinFileType.INSTANCE,
    JavaProjectRootsUtil.getScopeWithoutGeneratedSources(ProjectScope.getProjectScope(project), project)
  )
    .map { file -> PsiManager.getInstance(project).findFile(file) }
    .forEach { PsiTreeUtil.processElements(it, observableCountingProcessor) }

  FileTypeIndex.getFiles(
    JavaFileType.INSTANCE,
    JavaProjectRootsUtil.getScopeWithoutGeneratedSources(ProjectScope.getProjectScope(project), project)
  )
    .map { file -> PsiManager.getInstance(project).findFile(file) }
    .forEach { PsiTreeUtil.processElements(it, observableCountingProcessor) }

  return ObservableMetrics.newBuilder()
    .setPrimitiveCount(primitiveCount)
    .setCollectionCount(collectionCount)
    .setObservableObjectCount(objectCount)
    .build()
}

/**
 * Scans all java and kotlin source files to collect live data metrics, as defined in the proto [LiveDataMetrics].
 */
fun trackLiveData(project: Project): LiveDataMetrics {
  var liveDataCount = 0
  val liveDataCountingProcessor = PsiElementProcessor<PsiElement> { psiElement ->
    if (isLiveData(psiElement)) {
      liveDataCount ++
    }
    true
  }
  FileTypeIndex.getFiles(
    KotlinFileType.INSTANCE,
    JavaProjectRootsUtil.getScopeWithoutGeneratedSources(ProjectScope.getProjectScope(project), project)
  )
    .map { file -> PsiManager.getInstance(project).findFile(file) }
    .forEach { PsiTreeUtil.processElements(it, liveDataCountingProcessor) }

  FileTypeIndex.getFiles(
    JavaFileType.INSTANCE,
    JavaProjectRootsUtil.getScopeWithoutGeneratedSources(ProjectScope.getProjectScope(project), project)
  )
    .map { file -> PsiManager.getInstance(project).findFile(file) }
    .forEach { PsiTreeUtil.processElements(it, liveDataCountingProcessor) }

  return LiveDataMetrics.newBuilder()
    .setLiveDataObjectCount(liveDataCount)
    .build()
}

private fun isObservablePrimitive(element: PsiElement): Boolean {
  val type = getTypeName(element) ?: return false
  return OBSERVABLE_PRIMITIVES.contains(type)
}

private fun isObservableCollection(element: PsiElement): Boolean {
  val type = getTypeName(element) ?: return false
  return OBSERVABLE_COLLECTIONS.contains(type)
}

private fun isLiveData(element: PsiElement): Boolean {
  val type = getTypeName(element) ?: return false
  return LIVE_DATA_OBJECTS.contains(type)
}
/**
 * Gets the current type of the target element, if it's a java field or kotlin property. Otherwise return `null`.
 */
private fun getTypeName(element: PsiElement): String? = when (element) {
  is KtProperty -> element.typeName
  is PsiField -> element.typeName
  else -> null
}

/**
 * Gets the simple name of this [KtProperty]'s type without the trailing <T> template suffix (if it has one),
 * or `null` if at any point in the chain of API calls returns null.
 *
 * The reason we need to perform nested calls to children is because the current property is a KtProperty which includes the entire property
 * declaration, ex: val observableInt = ObservableInt(1), its child is a KtCallExpression namely `ObservableInt(1)`, whose first child is a
 * KtNameReferenceExpression `ObservableInt`.
 */
private val KtProperty.typeName: String?
  get() = (this as PsiElement).children.firstOrNull()?.children?.firstOrNull()?.text?.substringBefore("<")

/**
 * Gets the simple name of this [PsiField]'s type without the trailing <T> template suffix (if it has one),
 * or `null` if at any point in the chain of API calls returns null.
 */
private val PsiField.typeName: String?
  get() = type.presentableText.substringBefore("<")

private fun isObservableObject(element: PsiElement) = when (element) {
  is KtClass -> isKtClassSubclassOfObservable(element)
  is PsiClass -> isPsiClassSubclassOfObservable(element)
  else -> false
}

/**
 * Gets the fully qualified name of a kotlin type. Copied from TypeUtil.kt.
 */
private val KotlinType.fqNameSafe
  get() = constructor.declarationDescriptor?.fqNameSafe

/**
 * Checks whether the current kotlin type is a subclass of a class denoted by [className]. Copied from TypeUtil.kt.
 */
private fun KotlinType.isSubclassOf(className: String, strict: Boolean = false): Boolean {
  return (!strict && fqNameSafe?.asString() == className) || constructor.supertypes.any {
    it.fqNameSafe?.asString() == className || it.isSubclassOf(className, true)
  }
}

/**
 * Gets the fully qualified name of databinding's Observable class. The name should be androidx.databinding.Observable when mode is AndroidX
 * and android.databinding.Observable when mode is SUPPORT. Return null when data binding is not enabled (which shouldn't be a case here).
 */
private fun getObservableClassFullName(facet: AndroidFacet) = "${ModuleDataBinding.getInstance(facet).dataBindingMode.packageName}Observable"

private fun isKtClassSubclassOfObservable(clazz: KtClass): Boolean {
  val facet = clazz.androidFacet ?: return false
  val fqObservableName = getObservableClassFullName(facet)
  return (clazz.descriptor as ClassDescriptor).defaultType.isSubclassOf(fqObservableName)
}

private fun isPsiClassSubclassOfObservable(clazz: PsiClass): Boolean {
  val facet = clazz.androidFacet ?: return false
  val fqObservableName = getObservableClassFullName(facet)
  return InheritanceUtil.isInheritor(clazz, fqObservableName)
}