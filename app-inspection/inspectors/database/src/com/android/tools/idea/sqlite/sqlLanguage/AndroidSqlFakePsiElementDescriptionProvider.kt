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
package com.android.tools.idea.sqlite.sqlLanguage

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation

/**
 * Provides description for [AndroidSqlFakePsiElement].
 *
 * This text is used for example in hint when you press ctrl and hover element
 */
class AndroidSqlFakePsiElementDescriptionProvider : ElementDescriptionProvider {
  override fun getElementDescription(
    element: PsiElement,
    location: ElementDescriptionLocation
  ): String? {
    if (element is AndroidSqlFakePsiElement) {
      when (location) {
        UsageViewShortNameLocation.INSTANCE -> return element.name
        UsageViewTypeLocation.INSTANCE -> return element.typeDescription
        else -> return ""
      }
    }
    return null
  }
}
