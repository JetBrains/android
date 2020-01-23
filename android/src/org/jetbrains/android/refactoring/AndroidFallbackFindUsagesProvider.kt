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
package org.jetbrains.android.refactoring

import com.android.tools.idea.util.androidFacet
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.EmptyFindUsagesProvider
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.android.tools.idea.res.isResourceFile

/**
 * [FindUsagesProvider] for resource images files and any other files that are special in Android projects.
 *
 * This provides a description in the UsageView for these resources, if they are returned as usages from "find usages" or other refactorings
 * like [org.jetbrains.android.refactoring.UnusedResourcesProcessor].
 */
class AndroidFallbackFindUsagesProvider : FindUsagesProvider {
  companion object {
    private val empty = EmptyFindUsagesProvider()

    fun isBinaryResourceFile(element: PsiElement): Boolean {
      val facet = element.androidFacet ?: return false
      return element is PsiBinaryFile && isResourceFile(element.virtualFile, facet)
    }
  }

  override fun canFindUsagesFor(element: PsiElement): Boolean {
    // FindUsagesProvider API has two "parts": enabling "find usages" for certain PSI elements and controlling how UsageInfo instances are
    // rendered in the "usages" tool window. We only want to extend the latter, so we always return false here.
    return false
  }

  override fun getType(element: PsiElement): String {
    return if (isBinaryResourceFile(element)) "Android resource file" else empty.getType(element)
  }

  override fun getDescriptiveName(element: PsiElement): String {
    return if (!isBinaryResourceFile(element)) {
      empty.getDescriptiveName(element)
    } else {
      val file = element as PsiBinaryFile
      val dir = file.containingDirectory
      if (dir != null) {
        "${dir.name}/${file.name}"
      }
      else {
        file.name
      }
    }
  }
  override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
    return if (isBinaryResourceFile(element)) {
      getDescriptiveName(element)
    }
    else {
      empty.getNodeText(element, useFullName)
    }
  }

  override fun getHelpId(element: PsiElement): String? {
    return if (isBinaryResourceFile(element)) null else empty.getHelpId(element)
  }

  override fun getWordsScanner(): WordsScanner? = null
}
