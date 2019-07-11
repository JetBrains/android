/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl.findUsages

import com.android.tools.idea.lang.aidl.psi.AidlDeclaration
import com.android.tools.idea.lang.aidl.psi.AidlDeclarationName
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement

/**
 * Find Usage factory for AIDL files. Finds the usages of the corresponding generated Psi elements.
 */
class AidlFindUsageHandlerFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean {
    return element is AidlDeclarationName
  }

  override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
    return object : FindUsagesHandler(element) {
      override fun getSecondaryElements(): Array<PsiElement> {
        when (element) {
          is AidlDeclarationName -> {
            val generatedElement = (element.getParent() as AidlDeclaration).generatedPsiElement ?: return PsiElement.EMPTY_ARRAY
            return arrayOf(generatedElement as PsiElement)
          }
        }
        return PsiElement.EMPTY_ARRAY
      }
    }
  }
}
