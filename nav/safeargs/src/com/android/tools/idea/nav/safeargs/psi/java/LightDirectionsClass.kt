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
import com.android.ide.common.resources.ResourceItem
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.index.NavXmlData
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementById
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * Light class for Directions classes generated from navigation xml files.
 *
 * A "Direction" represents functionality that takes you away from one destination to another.
 *
 * For example, if you had the following "nav.xml":
 *
 * ```
 *  <navigation>
 *    <fragment id="@+id/mainMenu">
 *      <action id="@+id/actionToOptions" />
 *    <argument
 *        android:name="message"
 *        app:argType="string" />
 *
 *      <destination="@id/options" />
 *    </fragment>
 *    <fragment id="@+id/options">
 *      <action id="@+id/actionToMainMenu" />
 *     </fragment>
 *  </navigation>
 * ```
 *
 * This would generate a class like the following:
 *
 * ```
 *  class MainMenuDirections {
 *    static NavDirections actionToOptions();
 *  }
 *
 *  class OptionsDirections {
 *    static ActionToMainMenu actionToOptions(String message);
 *
 *    static class ActionToMainMenu implements NavDirections {
 *      String getMessage();
 *      String setMessage();
 *    }
 *  }
 *
 * ```
 */
class LightDirectionsClass(private val facet: AndroidFacet,
                           private val modulePackage: String,
                           navigationResource: ResourceItem,
                           private val data: NavXmlData,
                           private val destination: NavDestinationData)
  : SafeArgsLightBaseClass(facet, modulePackage, "Directions", navigationResource, destination) {
  init {
    setModuleInfo(facet.module, false)
  }

  private val actionClasses by lazy { computeInnerClasses() }
  private val _methods by lazy { computeMethods() }
  private val _navigationElement by lazy { backingResourceFile?.findXmlTagById(destination.id) }

  override fun getMethods() = _methods
  override fun getAllMethods() = methods
  override fun findMethodsByName(name: String, checkBases: Boolean): Array<PsiMethod> {
    return allMethods.filter { method -> method.name == name }.toTypedArray()
  }

  override fun getInnerClasses() = actionClasses

  override fun findInnerClassByName(name: String, checkBases: Boolean): PsiClass? {
    return actionClasses.find { it.name == name }
  }

  override fun getNavigationElement(): PsiElement {
    return _navigationElement ?: return super.getNavigationElement()
  }

  private fun computeMethods(): Array<PsiMethod> {
    val navDirectionsType = parsePsiType(modulePackage, "androidx.navigation.NavDirections", null, this)
    return destination.actions
      .mapNotNull { action ->
        val targetDestination = data.root.allDestinations.firstOrNull { it.id == action.destination } ?: return@mapNotNull null
        val types = targetDestination.arguments.map { arg ->
          arg to parsePsiType(modulePackage, arg.type, arg.defaultValue, this)
        }

        val methodName = action.id.toCamelCase()
        val resolvedNavigationElement = (_navigationElement as? XmlTag)?.findChildTagElementById(SdkConstants.TAG_ACTION, action.id)
        val resolvedNavDirectionsType = actionClasses.find { it.name!!.decapitalize() == methodName }
                                          ?.let { PsiTypesUtil.getClassType(it) }
                                        ?: navDirectionsType
        createMethod(name = methodName,
                     navigationElement = resolvedNavigationElement,
                     modifiers = MODIFIERS_STATIC_PUBLIC_METHOD,
                     returnType = annotateNullability(resolvedNavDirectionsType))
          .apply {
            types.forEach {
              val arg = it.first
              val type = it.second // We know it's non-null because of the "any" check above
              this.addParameter(arg.name, type)
            }
          }
      }.toTypedArray()
  }

  private fun computeInnerClasses(): Array<PsiClass> {
    return destination.actions
      .mapNotNull { action ->
        val targetDestination = data.root.allDestinations.firstOrNull { it.id == action.destination } ?: return@mapNotNull null
        targetDestination.arguments.takeUnless { it.isEmpty() } ?: return@mapNotNull null

        val innerClassName = action.id.toUpperCamelCase()
        LightActionBuilderClass(innerClassName, targetDestination, backingResourceFile, facet, modulePackage, this) }
      .toTypedArray()
  }
}

