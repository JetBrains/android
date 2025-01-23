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

import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.module.NavEntry
import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import org.jetbrains.android.augment.AndroidLightClassBase

/** Common functionality for all safe args light classes. */
abstract class SafeArgsLightBaseClass
private constructor(
  protected val navInfo: NavInfo,
  protected val navEntry: NavEntry,
  val destination: NavDestinationData,
  names: Names,
) :
  AndroidLightClassBase(
    PsiManager.getInstance(navInfo.facet.module.project),
    setOf(PsiModifier.PUBLIC, PsiModifier.FINAL),
    ContainingFileProvider.Builder(names.qualified),
    AndroidLightClassModuleInfo.from(navInfo.facet.module),
  ) {

  protected constructor(
    navInfo: NavInfo,
    navEntry: NavEntry,
    destination: NavDestinationData,
    suffix: String,
  ) : this(navInfo, navEntry, destination, getNames(navInfo, destination, suffix))

  private val qualifiedName = names.qualified
  private val name = names.simple

  override fun getName() = name

  override fun getQualifiedName() = qualifiedName

  override fun isValid() = true

  override fun getNavigationElement(): PsiElement {
    return navEntry.backingXmlFile ?: return super.getNavigationElement()
  }

  private data class Names(val qualified: String, val simple: String)

  companion object {
    private fun getNames(navInfo: NavInfo, destination: NavDestinationData, suffix: String): Names {
      val qualifiedName =
        destination.name.let { name ->
          val nameWithoutSuffix = if (!name.startsWith('.')) name else "${navInfo.packageName}$name"
          "$nameWithoutSuffix$suffix"
        }

      return Names(qualifiedName, qualifiedName.substringAfterLast('.'))
    }
  }
}
