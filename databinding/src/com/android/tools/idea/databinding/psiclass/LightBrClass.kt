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
package com.android.tools.idea.databinding.psiclass

import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.project.ProjectLayoutResourcesModificationTracker
import com.android.tools.idea.databinding.util.BrUtil
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.collect.ImmutableSet
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.light.LightField
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtil
import org.jetbrains.android.augment.AndroidLightClassBase
import org.jetbrains.android.facet.AndroidFacet

/**
 * _all is a special BR constant which triggers changes on all observables; it is used to indicate
 * the entire observable changed.
 */
private const val ALL_FIELD = "_all"

/**
 * In-memory PSI that represents a data binding BR file.
 *
 * See also: https://developer.android.com/topic/libraries/data-binding/generated-binding#advanced_binding
 */
class LightBrClass(psiManager: PsiManager, private val facet: AndroidFacet, private val qualifiedName: String) :
  AndroidLightClassBase(psiManager, ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL)) {

  /**
   * All fields in this BR class, including the top "_all" field
   */
  val allFieldNames
    get() = fieldCache.value.map { field -> field.name }.toTypedArray()

  private val fieldCache: CachedValue<Array<PsiField>>
  private val containingFile: PsiFile

  init {
    val project = facet.module.project
    val resourcesModifiedTracker = ProjectLayoutResourcesModificationTracker.getInstance(project)
    fieldCache = CachedValuesManager.getManager(project).createCachedValue {
      val variableNamesList = mutableListOf(ALL_FIELD)
      run {
        val groups = LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.takeIf { it.isNotEmpty() } ?: return@run

        val variableNamesSet = groups
          .flatMap { group -> group.layouts }
          .flatMap { layout -> layout.data.variables }
          .map { variable -> variable.name }
          .toMutableSet()
        collectVariableNamesFromUserBindables()?.let { bindables -> variableNamesSet.addAll(bindables) }

        variableNamesList.addAll(variableNamesSet.sorted())
      }

      val elementFactory = PsiElementFactory.getInstance(project)
      val psiFields = variableNamesList
        .map { name -> createPsiField(project, elementFactory, name) }
        .toTypedArray()

      // TODO(b/147513068): Reliance on javaStructureModificationTracker is known to cause performance problems.
      CachedValueProvider.Result.create(psiFields, resourcesModifiedTracker,
                                        psiManager.modificationTracker.javaStructureModificationTracker)
    }

    setModuleInfo(facet.module, false)

    // Create a fake backing file to represent this BR file
    val factory = PsiFileFactory.getInstance(project)
    val backingFile = factory.createFileFromText("BR.java", JavaFileType.INSTANCE,
                                                 "// This class is generated on-the-fly by the IDE.") as PsiJavaFile
    backingFile.packageName = qualifiedName.replace(".BR", "")
    containingFile = backingFile
  }

  /**
   * Search for all `@Bindable` annotated fields in user code only, excluding those found in
   * generated code, since those were `@Bindable` fields were generated FROM variable names.
   */
  private fun collectVariableNamesFromUserBindables(): Set<String>? {
    val facade = JavaPsiFacade.getInstance(facet.module.project)
    val mode = LayoutBindingModuleCache.getInstance(facet).dataBindingMode
    val moduleScope = facet.getModuleSystem().getResolveScope(ScopeType.MAIN)
    val bindableAnnotation = facade.findClass(mode.bindable, moduleScope) ?: return null

    // Generated code with @Bindable annotations is identified as classes that inherit from
    // ViewDataBinding. User code should never do this.
    val psiElements =
      AnnotatedElementsSearch.searchElements<PsiModifierListOwner>(
        bindableAnnotation, moduleScope, PsiMethod::class.java, PsiField::class.java)
        // Asserting non-null as we are confident that @Bindable fields exist within a class
        .filter { element -> PsiUtil.getTopLevelClass(element)!!.superClass!!.qualifiedName != mode.viewDataBinding }

    return BrUtil.collectIds(psiElements)
  }

  private fun createPsiField(project: Project, factory: PsiElementFactory, id: String): PsiField {
    val field = factory.createField(id, PsiTypes.intType())
    PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true)
    PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true)
    PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true)
    return LightBRField(PsiManager.getInstance(project), field, this)
  }

  override fun getQualifiedName(): String {
    return qualifiedName
  }

  override fun getName(): String {
    return DataBindingUtil.BR
  }

  override fun getContainingClass(): PsiClass? {
    return null
  }

  override fun getFields(): Array<PsiField> {
    return fieldCache.value
  }

  override fun getAllFields(): Array<PsiField> {
    return fields
  }

  override fun getContainingFile(): PsiFile {
    return containingFile
  }

  override fun getNameIdentifier(): PsiIdentifier {
    return LightIdentifier(manager, name)
  }

  override fun getNavigationElement(): PsiElement {
    return containingFile
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun canNavigate(): Boolean {
    return false
  }

  override fun canNavigateToSource(): Boolean {
    return false
  }

  /**
   * The light field representing elements of BR class
   */
  internal class LightBRField(manager: PsiManager, field: PsiField, containingClass: PsiClass)
    : LightField(manager, field, containingClass), ModificationTracker {

    override fun getModificationCount(): Long {
      // See http://b.android.com/212766
      // The field can't change; it's computed on the fly.
      // Needed by the LightBrClass field cache.
      return 0
    }
  }
}
