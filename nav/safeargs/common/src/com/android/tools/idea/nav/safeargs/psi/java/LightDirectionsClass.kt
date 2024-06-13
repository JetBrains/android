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
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.module.NavEntry
import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.android.tools.idea.nav.safeargs.psi.ArgumentUtils.getActionsWithResolvedArguments
import com.android.tools.idea.nav.safeargs.psi.xml.findFirstMatchingElementByTraversingUp
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.android.utils.usLocaleDecapitalize
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTypesUtil

/**
 * Light class for Directions classes generated from navigation xml files.
 *
 * A "Direction" represents functionality that takes you away from one destination to another.
 *
 * For example, if you had the following "nav.xml":
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
class LightDirectionsClass(navInfo: NavInfo, navEntry: NavEntry, destination: NavDestinationData) :
  SafeArgsLightBaseClass(navInfo, navEntry, destination, "Directions") {
  private val LOG
    get() = Logger.getInstance(LightDirectionsClass::class.java)

  private val actionClasses by lazy { computeInnerClasses() }
  private val _methods by lazy { computeMethods() }
  private val _navigationElement by lazy { navEntry.backingXmlFile?.findXmlTagById(destination.id) }
  private val _actions by lazy {
    destination.getActionsWithResolvedArguments(navEntry.data, navInfo.packageName)
  }

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
    val navDirectionsType =
      parsePsiType(navInfo.packageName, "androidx.navigation.NavDirections", null, this)
    return _actions
      .map { action ->
        val methodName = action.id.toCamelCase()
        val resolvedNavigationElement =
          _navigationElement?.findFirstMatchingElementByTraversingUp(
            SdkConstants.TAG_ACTION,
            action.id,
          )
        val resolvedNavDirectionsType =
          actionClasses
            .find { it.name!!.usLocaleDecapitalize() == methodName }
            ?.let { PsiTypesUtil.getClassType(it) } ?: navDirectionsType
        createMethod(
            name = methodName,
            navigationElement = resolvedNavigationElement,
            modifiers = MODIFIERS_STATIC_PUBLIC_METHOD,
            returnType = annotateNullability(resolvedNavDirectionsType),
          )
          .apply {
            action.arguments.forEach { arg ->
              if (arg.defaultValue == null) {
                val argType = arg.parsePsiType(navInfo.packageName, this)
                this.addParameter(arg.name.toCamelCase(), argType)
              }
            }
          }
      }
      .toTypedArray()
  }

  private fun computeInnerClasses(): Array<PsiClass> {
    return _actions
      .mapNotNull { action ->
        action.arguments.takeUnless { it.isEmpty() } ?: return@mapNotNull null

        val innerClassName = action.id.toUpperCamelCase()
        LightActionBuilderClass(innerClassName, navInfo, this, action, navEntry.backingXmlFile)
      }
      .toTypedArray()
  }
}
