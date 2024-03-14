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
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache.Companion.getInstance
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.collect.Maps
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.AtomicNotNullLazyValue
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
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
class LightDataBindingComponentClass(psiManager: PsiManager, private val myFacet: AndroidFacet) :
  AndroidLightClassBase(psiManager, ImmutableSet.of<String>(PsiModifier.PUBLIC)),
  ModificationTracker {
  private val myMethodCache: CachedValue<Array<PsiMethod>>
  private val myContainingFile: AtomicNotNullLazyValue<PsiFile>
  private val myMode = getInstance(myFacet).dataBindingMode

  init {
    val project = myFacet.module.project
    val modificationTracker = AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(project)

    myMethodCache =
      CachedValuesManager.getManager(project)
        .createCachedValue<Array<PsiMethod>>(
          CachedValueProvider<Array<PsiMethod?>> {
            val instanceAdapterClasses: MutableMap<String, MutableSet<String?>> = Maps.newHashMap()
            val facade = JavaPsiFacade.getInstance(myFacet.module.project)
            val moduleScope = myFacet.getModuleSystem().getResolveScope(ScopeType.MAIN)
            val aClass =
              facade.findClass(myMode.bindingAdapter, moduleScope)
                ?: return@createCachedValue CachedValueProvider.Result.create<Array<PsiMethod>>(
                  PsiMethod.EMPTY_ARRAY,
                  modificationTracker,
                )

            val psiElements: Collection<PsiModifierListOwner> =
              AnnotatedElementsSearch.searchElements(
                  aClass,
                  myFacet.module.moduleScope,
                  PsiMethod::class.java,
                )
                .findAll()
            var methodCount = 0

            for (owner in psiElements) {
              if (owner is PsiMethod && !owner.hasModifierProperty(PsiModifier.STATIC)) {
                val containingClass = owner.containingClass ?: continue
                val className = containingClass.name!!
                var set = instanceAdapterClasses[className]
                if (set == null) {
                  set = TreeSet()
                  instanceAdapterClasses[className] = set
                }
                if (set.add(containingClass.qualifiedName)) {
                  methodCount++
                }
              }
            }
            if (methodCount == 0) {
              return@createCachedValue CachedValueProvider.Result.create<Array<PsiMethod>>(
                PsiMethod.EMPTY_ARRAY,
                modificationTracker,
              )
            }
            val elementFactory = PsiElementFactory.getInstance(project)
            val result = arrayOfNulls<PsiMethod>(methodCount)
            var methodIndex = 0
            val scope = GlobalSearchScope.allScope(project)
            for ((key, value) in instanceAdapterClasses) {
              if (value.size == 1) {
                result[methodIndex] =
                  createPsiMethod(
                    elementFactory,
                    "get$key",
                    Iterables.getFirst(value, ""),
                    project,
                    scope,
                  )
                methodIndex++
              } else {
                var suffix = 1
                for (item in value) {
                  val name = "get$key$suffix"
                  result[methodIndex] = createPsiMethod(elementFactory, name, item, project, scope)
                  suffix++
                  methodIndex++
                }
              }
            }
            CachedValueProvider.Result.create<Array<PsiMethod?>>(result, modificationTracker)
          },
          false,
        )

    myContainingFile =
      AtomicNotNullLazyValue.createValue {
        var packageName = myMode.packageName
        if (packageName.endsWith(".")) {
          packageName = packageName.substring(0, packageName.length - 1)
        }
        PsiFileFactory.getInstance(myFacet.module.project)
          .createFileFromText(
            SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT + ".java",
            JavaLanguage.INSTANCE,
            """
          package $packageName;
          public interface DataBindingComponent {}
          """
              .trimIndent(),
            false,
            true,
            true,
          )
      }

    setModuleInfo(myFacet.module, false)
  }

  override fun isInterface(): Boolean {
    return true
  }

  override fun isAnnotationType(): Boolean {
    return false
  }

  private fun createPsiMethod(
    factory: PsiElementFactory,
    name: String,
    type: String?,
    project: Project,
    scope: GlobalSearchScope,
  ): PsiMethod {
    val method = factory.createMethod(name, PsiType.getTypeByName(type!!, project, scope))
    PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true)
    PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true)
    return LightMethod(PsiManager.getInstance(project), method, this)
  }

  override fun getQualifiedName(): String? {
    return myMode.dataBindingComponent
  }

  override fun getName(): String? {
    return SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT
  }

  override fun getContainingClass(): PsiClass? {
    return null
  }

  override fun getFields(): Array<PsiField> {
    return PsiField.EMPTY_ARRAY
  }

  override fun getAllFields(): Array<PsiField> {
    return fields
  }

  override fun getMethods(): Array<PsiMethod> {
    return myMethodCache.value
  }

  override fun getAllMethods(): Array<PsiMethod> {
    return methods
  }

  override fun findMethodsByName(name: @NonNls String?, checkBases: Boolean): Array<PsiMethod> {
    val result: MutableList<PsiMethod> = ArrayList()
    for (method in myMethodCache.value) {
      if (method.name == name) {
        result.add(method)
      }
    }
    return if (result.isEmpty()) PsiMethod.EMPTY_ARRAY else result.toArray(PsiMethod.EMPTY_ARRAY)
  }

  override fun getContainingFile(): PsiFile? {
    return myContainingFile.value
  }

  override fun getNameIdentifier(): PsiIdentifier? {
    return LightIdentifier(manager, name)
  }

  override fun getNavigationElement(): PsiElement {
    val containingFile = containingFile
    return containingFile ?: super.getNavigationElement()
  }

  override fun getModificationCount(): Long {
    return 0
  }
}
