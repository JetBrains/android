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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
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
 *      <destination="@id/options" />
 *    </fragment>
 *    <fragment id="@+id/options">
 *  </navigation>
 * ```
 *
 * This would generate a class like the following:
 *
 * ```
 *  class MainMenuDirections {
 *    class ActionToOptions {}
 *    ActionToOptions actionToOptions();
 *  }
 * ```
 */
class LightDirectionsClass(facet: AndroidFacet,
                           private val modulePackage: String,
                           navigationResource: ResourceItem,
                           private val data: NavXmlData,
                           private val destination: NavDestinationData)
  : SafeArgsLightBaseClass(facet, modulePackage, "Directions", navigationResource, destination) {
  init {
    setModuleInfo(facet.module, false)
  }

  private val _methods by lazy { computeMethods() }
  private val _navigationElement by lazy { backingResourceFile?.findXmlTagById(destination.id) }

  override fun getMethods() = _methods
  override fun getAllMethods() = methods
  override fun findMethodsByName(name: String, checkBases: Boolean): Array<PsiMethod> {
    return allMethods.filter { method -> method.name == name }.toTypedArray()
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
        createMethod(name = methodName,
                     navigationElement = resolvedNavigationElement,
                     modifiers = MODIFIERS_STATIC_PUBLIC_METHOD,
                     returnType = annotateNullability(navDirectionsType))
          .apply {
            types.forEach {
              val arg = it.first
              val type = it.second // We know it's non-null because of the "any" check above
              this.addParameter(arg.name, type)
            }
          }
      }.toTypedArray()
  }
}

