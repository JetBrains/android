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
import com.android.tools.idea.nav.safeargs.index.NavActionData
import com.android.tools.idea.nav.safeargs.index.NavArgumentData
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.index.NavXmlData
import com.android.tools.idea.nav.safeargs.psi.xml.findFirstMatchingElementByTraversingUp
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.android.utils.usLocaleDecapitalize
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTypesUtil
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
 *      <action id="@+id/actionToOptions"
 *        destination="@id/options" />
 *      <argument
 *        android:name="message"
 *        app:argType="string" />
 *    </fragment>
 *
 *    <fragment id="@+id/options">
 *      <action id="@+id/actionToMainMenu"
 *        destination="@id/mainMenu"/>
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
 *    static OptionsDirections.ActionToMainMenu actionToMainMenu(String message);
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

  private val LOG get() = Logger.getInstance(LightDirectionsClass::class.java)
  private val actionClasses by lazy { computeInnerClasses() }
  private val _methods by lazy { computeMethods() }
  private val _navigationElement by lazy { backingResourceFile?.findXmlTagById(destination.id) }
  private val _actions by lazy { destination.resolveActions() }

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
    return _actions
      .map { action ->
        val methodName = action.id.toCamelCase()
        val resolvedNavigationElement = _navigationElement?.findFirstMatchingElementByTraversingUp(SdkConstants.TAG_ACTION, action.id)
        val resolvedNavDirectionsType = actionClasses.find { it.name!!.usLocaleDecapitalize() == methodName }
                                          ?.let { PsiTypesUtil.getClassType(it) }
                                        ?: navDirectionsType
        createMethod(name = methodName,
                     navigationElement = resolvedNavigationElement,
                     modifiers = MODIFIERS_STATIC_PUBLIC_METHOD,
                     returnType = annotateNullability(resolvedNavDirectionsType))
          .apply {
            action.arguments.forEach { arg ->
              if (arg.defaultValue == null) {
                val argType = parsePsiType(modulePackage, arg.type, arg.defaultValue, this)
                this.addParameter(arg.name.toCamelCase(), argType)
              }
            }
          }

      }.toTypedArray()
  }

  private fun computeInnerClasses(): Array<PsiClass> {
    return _actions
      .mapNotNull { action ->
        action.arguments.takeUnless { it.isEmpty() } ?: return@mapNotNull null

        val innerClassName = action.id.toUpperCamelCase()
        LightActionBuilderClass(innerClassName, backingResourceFile, facet, modulePackage, this, action)
      }
      .toTypedArray()
  }

  /**
   * For each of action, besides args from target destination, args from its surrounding action are collected to
   * support args overrides.
   * (https://developer.android.com/guide/navigation/navigation-pass-data#override_a_destination_argument_in_an_action)
   */
  private fun NavDestinationData.resolveActions(): List<NavActionData> {
    return this.actions
      .mapNotNull { action ->
        val destinationId = action.resolveDestination() ?: return@mapNotNull null

        // Null implies only 'popUpTo' attribute is defined, so no args are supposed to be passed.
        action.destination ?: return@mapNotNull object : NavActionData by action {
          override val arguments: List<NavArgumentData> = emptyList()
        }

        val argsFromTargetDestination = data.resolvedDestinations.firstOrNull { it.id == destinationId }?.arguments
                                        ?: emptyList()

        val resolvedArguments = (action.arguments + argsFromTargetDestination)
          .groupBy { it.name }
          .map { entry ->
            if (entry.value.size > 1) checkArguments(entry)
            entry.value.first()
          }
        object : NavActionData by action {
          override val arguments: List<NavArgumentData> = resolvedArguments
        }
      }
      .toList()
  }

  /**
   * Warn if incompatible types of argument exist. We still provide best results though it fails to compile.
   */
  private fun checkArguments(entry: Map.Entry<String, List<NavArgumentData>>) {
    val types = entry.value
      .asSequence()
      .map { arg -> getPsiTypeStr(modulePackage, arg.type, arg.defaultValue) }
      .toSet()

    if (types.size > 1) LOG.warn("Incompatible types of argument ${entry.key}.")
  }
}

