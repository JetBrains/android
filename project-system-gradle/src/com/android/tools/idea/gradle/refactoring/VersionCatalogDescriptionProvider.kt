/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.refactoring

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewShortNameLocation
import org.toml.lang.psi.TomlKeySegment

class VersionCatalogDescriptionProvider : ElementDescriptionProvider {
  override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
    // to not interfere with plain toml files
    if(!isVersionCatalogFile(element)) return null
    if (element !is TomlKeySegment) return null
    if(isVersionCatalogAlias(element)) {
      if (location is UsageViewShortNameLocation) return element.name // return short name

      // in all other cases return description
      return "Version catalog alias '${element.name}'"
    }
    return null
  }
}