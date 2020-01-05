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
package com.android.tools.idea.nav.safeargs.psi

import com.android.ide.common.resources.ResourceItem
import com.android.tools.idea.nav.safeargs.index.NavFragmentData
import com.intellij.psi.PsiClass
import org.jetbrains.android.facet.AndroidFacet

/**
 * Light class for Args classes generated from navigation xml files.
 *
 * An "Arg" represents an argument which can get passed from one destination to another.
 *
 * For example, if you had the following "nav.xml":
 *
 * ```
 *  <action id="@+id/sendMessage" destination="@+id/editorFragment">
 *    <argument name="message" argType="string" />
 *  </action>
 * ```
 *
 * This would generate a class like the following:
 *
 * ```
 *  class EditorFragmentArgs {
 *    String getMessage();
 *  }
 * ```
 */
class LightArgsClass(facet: AndroidFacet, modulePackage: String, navigationResource: ResourceItem, val fragment: NavFragmentData)
  : SafeArgsLightBaseClass(facet, modulePackage, "Args", navigationResource, fragment.toDestination()) {

  val builderClass = LightArgsBuilderClass(facet, modulePackage, this)

  override fun getInnerClasses(): Array<PsiClass> = arrayOf(builderClass)
  override fun findInnerClassByName(name: String, checkBases: Boolean): PsiClass? {
    return builderClass.takeIf { it.name == name }
  }
}
