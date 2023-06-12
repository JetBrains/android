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
package com.android.tools.idea.nav.safeargs.psi.java

import com.android.SdkConstants
import com.android.ide.common.gradle.Version
import com.android.ide.common.resources.ResourceItem
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementByNameAttr
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.android.facet.AndroidFacet

/**
 * Light class for Args classes generated from navigation xml files.
 *
 * An "Arg" represents an argument which can get passed from one destination to another.
 *
 * For example, if you had the following "nav.xml":
 *
 * ```
 * <argument
 *    android:name="message"
 *    app:argType="string" />
 * ```
 *
 * This would generate a class like the following:
 *
 * ```
 *  class EditorFragmentArgs implements NavArgs {
 *    static EditorFragmentArgs fromBundle(Bundle bundle);
 *    static EditorFragmentArgs fromSavedStateHandle(SavedStateHandle handle);
 *    Bundle toBundle();
 *    SavedStateHandle toSavedStateHandle();
 *    String getMessage();
 *  }
 * ```
 */
class LightArgsClass(facet: AndroidFacet,
                     private val modulePackage: String,
                     private val navigationVersion: Version,
                     navigationResource: ResourceItem,
                     val destination: NavDestinationData)
  : SafeArgsLightBaseClass(facet, modulePackage, "Args", navigationResource, destination) {

  init {
    setModuleInfo(facet.module, false)
  }

  private val NAV_ARGS_FQCN = "androidx.navigation.NavArgs"
  private val builderClass = LightArgsBuilderClass(facet, modulePackage, this)
  private val _fields by lazy { computeFields() }
  private val _methods by lazy { computeMethods() }
  private val backingXmlTag by lazy { backingResourceFile?.findXmlTagById(destination.id) }
  private val navArgsType by lazy { PsiType.getTypeByName(NAV_ARGS_FQCN, project, this.resolveScope) }
  private val navArgsClass by lazy { JavaPsiFacade.getInstance(project).findClass(NAV_ARGS_FQCN, this.resolveScope) }

  override fun getImplementsListTypes() = arrayOf(navArgsType)
  override fun getSuperTypes() = arrayOf(navArgsType)
  override fun getSupers() = navArgsClass?.let { arrayOf(it) } ?: emptyArray()

  override fun getInnerClasses(): Array<PsiClass> = arrayOf(builderClass)
  override fun findInnerClassByName(name: String, checkBases: Boolean): PsiClass? {
    return builderClass.takeIf { it.name == name }
  }

  override fun getMethods() = _methods
  override fun getAllMethods() = methods
  override fun findMethodsByName(name: String, checkBases: Boolean): Array<PsiMethod> {
    return allMethods.filter { method -> method.name == name }.toTypedArray()
  }

  override fun getNavigationElement(): PsiElement {
    return backingXmlTag ?: return super.getNavigationElement()
  }

  private fun computeMethods(): Array<PsiMethod> {
    val thisType = PsiTypesUtil.getClassType(this)
    val bundleType = parsePsiType(modulePackage, "android.os.Bundle", null, this)
    val savedStateHandleType = parsePsiType(modulePackage, "androidx.lifecycle.SavedStateHandle", null, this)

    val methods = mutableListOf<PsiMethod>()

    methods.addAll(destination.arguments.map { arg ->
      val psiType = parsePsiType(modulePackage, arg.type, arg.defaultValue, this)
      createMethod(name = "get${arg.name.toUpperCamelCase()}",
                   navigationElement = getFieldNavigationElementByName(arg.name),
                   returnType = annotateNullability(psiType, arg.isNonNull()))
    })

    methods.add(createMethod(name = "fromBundle",
                             modifiers = MODIFIERS_STATIC_PUBLIC_METHOD,
                             returnType = annotateNullability(thisType))
                  .addParameter("bundle", bundleType))

    // Add on version specific methods since the navigation library side is keeping introducing new methods.
    if (navigationVersion >= SafeArgsFeatureVersions.FROM_SAVED_STATE_HANDLE) {
      methods.add(
        createMethod(
          name = "fromSavedStateHandle",
          modifiers = MODIFIERS_STATIC_PUBLIC_METHOD,
          returnType = annotateNullability(thisType)
        ).addParameter("savedStateHandle", savedStateHandleType)
      )
    }

    // Add on version specific methods since the navigation library side is keeping introducing new methods.
    if (navigationVersion >= SafeArgsFeatureVersions.TO_SAVED_STATE_HANDLE) {
      methods.add(createMethod(name = "toSavedStateHandle", returnType = annotateNullability(savedStateHandleType)))
    }

    methods.add(createMethod(
      name = "toBundle",
      returnType = annotateNullability(bundleType)
    ))

    return methods.toTypedArray()
  }

  private fun computeFields(): Array<PsiField> {
    return destination.arguments
      .asSequence()
      .map { arg ->
        val targetArgumentTag = backingXmlTag?.findChildTagElementByNameAttr(SdkConstants.TAG_ARGUMENT, arg.name)
        createField(arg, modulePackage, targetArgumentTag)
      }
      .toList()
      .toTypedArray()
  }

  fun getFieldNavigationElementByName(name: String): PsiElement? {
    return _fields.firstOrNull { it.name == name }?.navigationElement
  }
}
