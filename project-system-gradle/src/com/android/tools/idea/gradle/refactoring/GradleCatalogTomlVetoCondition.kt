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

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlKeySegment

/**
 * Only allow to rename declaration aliases.
 * Reference literals (version.ref value) do not go through this condition
 * as they have reference to library alias.
 */
class GradleCatalogTomlVetoCondition : Condition<PsiElement> {
  override fun value(psiElement: PsiElement): Boolean {
    // if in non version catalog file - exit
    if (!isVersionCatalogFile(psiElement)) return false
    if (psiElement is TomlKeySegment) {
      return !isVersionCatalogAlias(psiElement)
    }
    return true
  }
}