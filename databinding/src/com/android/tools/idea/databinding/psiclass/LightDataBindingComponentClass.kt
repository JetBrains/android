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

import com.android.SdkConstants
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtil
import java.util.TreeSet
import org.jetbrains.android.augment.AndroidLightClassBase
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.NonNls

/**
 * In-memory PSI for the generated DataBindingComponent class.
 *
 * Note: A DataBindingComponent's purpose is to be (optionally) subclassed in tests, to allow
 * overriding BindingAdapters with test-specific implementations if necessary. It is not expected
 * that a user would grab an instance of and interact with one, so its PSI fields and methods are
 * not implemented here.
 *
 * See also: https://developer.android.com/reference/android/databinding/DataBindingComponent
 */
class LightDataBindingComponentClass(psiManager: PsiManager, private val facet: AndroidFacet) :
  AndroidLightClassBase(psiManager, setOf(PsiModifier.PUBLIC)), ModificationTracker {

  private val dataBindingMode = LayoutBindingModuleCache.getInstance(facet).dataBindingMode

  private val methodCache: CachedValue<Array<PsiMethod>> =
    CachedValuesManager.getManager(facet.module.project).createCachedValue(::computeMethods)

  private val containingFile: NotNullLazyValue<PsiFile> =
    NotNullLazyValue.atomicLazy {
      val packageName = dataBindingMode.packageName
      val normalizedPackageName =
        if (packageName.endsWith(".")) packageName.substring(0, packageName.length - 1)
        else packageName
      PsiFileFactory.getInstance(facet.module.project)
        .createFileFromText(
          "${SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT}.java",
          JavaLanguage.INSTANCE,
          // language=Java
          """
          package $normalizedPackageName;
          public interface DataBindingComponent {}
          """
            .trimIndent(),
          false,
          true,
          true,
        )
    }

  init {
    setModuleInfo(facet.module, false)
  }

  override fun isInterface() = true

  override fun isAnnotationType() = false

  private fun createPsiMethod(
    factory: PsiElementFactory,
    name: String,
    type: String,
    project: Project,
    scope: GlobalSearchScope,
  ): PsiMethod {
    val method = factory.createMethod(name, PsiType.getTypeByName(type, project, scope))
    PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true)
    PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true)
    return LightMethod(PsiManager.getInstance(project), method, this)
  }

  private fun computeMethods(): CachedValueProvider.Result<Array<PsiMethod>> {
    val project = facet.module.project
    val modificationTracker = AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(project)

    val instanceAdapterClasses: MutableMap<String, MutableSet<String>> = mutableMapOf()
    val moduleScope = facet.getModuleSystem().getResolveScope(ScopeType.MAIN)
    val aClass =
      JavaPsiFacade.getInstance(project).findClass(dataBindingMode.bindingAdapter, moduleScope)
        ?: return CachedValueProvider.Result.create(PsiMethod.EMPTY_ARRAY, modificationTracker)

    val psiElements =
      AnnotatedElementsSearch.searchElements(
          aClass,
          facet.module.moduleScope,
          PsiMethod::class.java,
        )
        .findAll()
    var methodCount = 0

    val containingClasses =
      psiElements
        .filterIsInstance<PsiMethod>()
        .filterNot { it.hasModifierProperty(PsiModifier.STATIC) }
        .mapNotNull { it.containingClass }
    for (containingClass in containingClasses) {
      val className = requireNotNull(containingClass.name)
      val set = instanceAdapterClasses.getOrPut(className) { TreeSet() }
      if (set.add(requireNotNull(containingClass.qualifiedName))) {
        methodCount++
      }
    }
    if (methodCount == 0) {
      return CachedValueProvider.Result.create(PsiMethod.EMPTY_ARRAY, modificationTracker)
    }

    val elementFactory = PsiElementFactory.getInstance(project)
    val result = ArrayList<PsiMethod>(methodCount)
    val scope = GlobalSearchScope.allScope(project)
    for ((key, value) in instanceAdapterClasses) {
      if (value.size == 1) {
        result.add(createPsiMethod(elementFactory, "get$key", value.single(), project, scope))
      } else {
        var suffix = 1
        for (item in value) {
          val name = "get$key$suffix"
          result.add(createPsiMethod(elementFactory, name, item, project, scope))
          suffix++
        }
      }
    }
    return CachedValueProvider.Result.create(result.toTypedArray(), modificationTracker)
  }

  override fun getQualifiedName() = dataBindingMode.dataBindingComponent

  override fun getName() = SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT

  override fun getContainingClass() = null

  override fun getFields(): Array<PsiField> = PsiField.EMPTY_ARRAY

  override fun getAllFields() = getFields()

  override fun getMethods(): Array<PsiMethod> = methodCache.value

  override fun getAllMethods() = getMethods()

  override fun findMethodsByName(name: @NonNls String?, checkBases: Boolean): Array<PsiMethod> {
    val result = methodCache.value.filter { it.name == name }
    return if (result.isEmpty()) PsiMethod.EMPTY_ARRAY else result.toTypedArray()
  }

  override fun getContainingFile() = containingFile.value

  override fun getNameIdentifier() = LightIdentifier(manager, name)

  override fun getNavigationElement() = getContainingFile()

  override fun getModificationCount() = 0L
}
