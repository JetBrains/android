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
package com.android.tools.idea.lang.roomSql

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager


/**
 * Prevents renaming of fake PSI elements created by Room.
 *
 * There are cases when we can navigate to element (reference.resolveTo() != null), but don't want to allow user to rename it.
 * In such cases we wrap original element into [NotRenamableElement]
 * e.g see [com.android.tools.idea.lang.roomSql.resolution.RoomColumnPsiReference.resolve].
 */
class RoomRenameVetoCondition : Condition<PsiElement> {

  override fun value(psiElement: PsiElement): Boolean {
    return psiElement is NotRenamableElement
  }
}

/**
 * Wrapper for [PsiElement] that prevents renaming from reference that resolves to it.
 */
class NotRenamableElement(val delegate:PsiElement): PsiElement by delegate {
  override fun isEquivalentTo(another: PsiElement?): Boolean {
    return PsiManager.getInstance(delegate.project)
      .areElementsEquivalent(delegate, if (another is NotRenamableElement) another.delegate else another)
  }
}
