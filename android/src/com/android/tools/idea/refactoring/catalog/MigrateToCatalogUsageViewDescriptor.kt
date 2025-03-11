/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.refactoring.catalog

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor

internal class MigrateToCatalogUsageViewDescriptor(private val usageInfos: Array<UsageInfo>) :
  UsageViewDescriptor {
  override fun getElements(): Array<PsiElement> {
    return usageInfos.mapNotNull { it.element }.toTypedArray()
  }

  override fun getProcessedElementsHeader(): String {
    return "Dependencies to be replaced with a version catalog reference"
  }

  override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
    return "Library references ($usagesCount dependencies in $filesCount files)"
  }
}
