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
package com.android.tools.idea.uibuilder.model

import com.android.SdkConstants
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope

/**
 * All the packages to try for non qualified names, including the empty package in case that the
 * class was declared in the default namespace.
 */
private val NO_PREFIX_PACKAGES: Set<String> =
  ImmutableSet.of(
    SdkConstants.ANDROID_WIDGET_PREFIX,
    SdkConstants.ANDROID_VIEW_PKG,
    SdkConstants.ANDROID_WEBKIT_PKG,
    SdkConstants.ANDROID_APP_PKG,
    ""
  )

/** Returns true if the tagName is already a qualified name (contains a dot). */
private fun isQualifiedTagName(tagName: String): Boolean = tagName.contains('.')

/**
 * Returns the potential fully qualified class names for the given viewTag. If the viewTag is
 * already a fully qualified name it returns a [Sequence] with just that element.
 */
private fun getPotentialFqnClassNames(viewTag: String): Sequence<String> =
  if (isQualifiedTagName(viewTag)) {
    // We got a qualified name so we do not need to test with the NO_PREFIX_PACKAGES names
    sequenceOf(viewTag)
  } else {
    NO_PREFIX_PACKAGES.asSequence().map { it + viewTag }
  }

/**
 * Returns the [PsiClass]s corresponding to the given [viewTag] name or an empty array if none can
 * be found. This method can receive a custom [GlobalSearchScope] to restrict the search to a
 * specific scope. By default, the scope will be the given [Project]. Multiple classes for the same
 * fully qualified name could be found for example in different non-related modules.
 */
@JvmOverloads
fun findClassesForViewTag(
  project: Project,
  viewTag: String,
  scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
): Array<PsiClass> {
  val facade = JavaPsiFacade.getInstance(project)
  return getPotentialFqnClassNames(viewTag)
    .map { facade.findClasses(it, scope) }
    .filter { it.isNotEmpty() }
    .firstOrNull() ?: emptyArray()
}

/**
 * Returns the [PsiClass] corresponding to the given [viewTag] name or null if non can be found.
 * This method can receive a custom [GlobalSearchScope] to restrict the search to a specific scope.
 * By default, the scope will be the given [Project].
 */
@JvmOverloads
fun findClassForViewTag(
  project: Project,
  viewTag: String,
  scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
): PsiClass? {
  val facade = JavaPsiFacade.getInstance(project)
  return getPotentialFqnClassNames(viewTag).mapNotNull { facade.findClass(it, scope) }.firstOrNull()
}
