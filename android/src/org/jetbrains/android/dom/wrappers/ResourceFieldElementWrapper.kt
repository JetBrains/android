/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.dom.wrappers

import com.intellij.navigation.NavigationItem
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.usageView.UsageViewTypeLocation
import org.jetbrains.android.AndroidResourceRenameResourceProcessor
import org.jetbrains.android.augment.AndroidLightField

/**
 * Wrapper for [AndroidLightField] used during refactoring, to convince [RenameProcessor] the field should be passed to
 * [AndroidResourceRenameResourceProcessor].
 */
class ResourceFieldElementWrapper(
  private val wrappedElement: AndroidLightField
) : ResourceElementWrapper,
    PsiElement by wrappedElement,
    NavigationItem by wrappedElement {
  override fun getWrappedElement(): AndroidLightField = wrappedElement
  override fun isWritable(): Boolean = true

  class DescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
      val field = (element as? ResourceFieldElementWrapper)?.wrappedElement ?: return null
      return when (location) {
        UsageViewTypeLocation.INSTANCE -> "resource ${field.name}"
        else -> field.name
      }
    }
  }
}
