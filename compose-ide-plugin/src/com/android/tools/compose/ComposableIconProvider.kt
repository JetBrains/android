/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.compose

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.ui.RowIcon
import icons.StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION
import javax.swing.Icon
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.util.hasMatchingExpected
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier

/**
 * Returns Composable function icon for [KtFunction] elements that are composable, or null otherwise
 * to allow fallback to any other providers. This may be used in various places across the IDE; one
 * example is in the "Add Import" menu.
 */
class ComposableIconProvider : KotlinIconProvider() {

  override fun getIcon(psiElement: PsiElement, flags: Int): Icon? {
    if (psiElement is KtFunction && psiElement.hasComposableAnnotation()) {
      if (flags and Iconable.ICON_FLAG_VISIBILITY > 0) {
        return createRowIcon(COMPOSABLE_FUNCTION, getVisibilityIcon(psiElement.modifierList))
      }

      return COMPOSABLE_FUNCTION
    }

    return null
  }

  override fun isDumbAware(): Boolean {
    // This provider can't run in dumb mode since it requires looking up annotations.
    return false
  }

  override fun isMatchingExpected(declaration: KtDeclaration): Boolean {
    return declaration.hasActualModifier() && declaration.hasMatchingExpected()
  }

  private fun createRowIcon(baseIcon: Icon, visibilityIcon: Icon): RowIcon =
    RowIcon(2).apply {
      setIcon(baseIcon, /* layer= */ 0)
      setIcon(visibilityIcon, /* layer= */ 1)
    }
}
