/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview

import com.intellij.codeInspection.reference.EntryPoint
import com.intellij.codeInspection.reference.RefElement
import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serializeObjectInto
import com.intellij.psi.PsiElement
import org.jdom.Element
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

/**
 * [EntryPoint] implementation to mark `@Preview` functions as entry points and avoid them being
 * flagged as unused.
 */
class WearTilePreviewEntryPoint : EntryPoint() {
  private var isSelected: Boolean = true

  override fun isEntryPoint(refElement: RefElement, psiElement: PsiElement): Boolean =
    isEntryPoint(psiElement)

  override fun isEntryPoint(psiElement: PsiElement): Boolean =
    isSelected &&
      psiElement
        .takeIf { it.isMethodWithTilePreviewSignature() }
        ?.toUElement(UMethod::class.java)
        .hasTilePreviewAnnotation()

  override fun readExternal(element: Element) = element.deserializeInto(this)

  override fun writeExternal(element: Element) {
    serializeObjectInto(this, element)
  }

  override fun getDisplayName(): String = "Wear Tile Preview"

  override fun isSelected(): Boolean = isSelected

  override fun setSelected(selected: Boolean) {
    this.isSelected = selected
  }
}
