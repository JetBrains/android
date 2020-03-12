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
package com.android.tools.idea.customview.preview

import com.android.SdkConstants.CLASS_VIEW
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile

internal const val CUSTOM_VIEW_PREVIEW_ID = "android-custom-view"

private val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

internal class CustomViewLightVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  override fun getParent() = FAKE_LAYOUT_RES_DIR
}

fun PsiClass.extendsView(): Boolean {
  return this.qualifiedName == CLASS_VIEW || this.extendsListTypes.any {
    it.resolve()?.extendsView() ?: false
  }
}

internal fun PsiFile.containsViewSuccessor(): Boolean {
  if (DumbService.isDumb(this.project)) {
    return false
  }

  // Quickly reject non-custom view files. A custom view constructor should have Context and AttributeSet as parameters
  // (https://developer.android.com/training/custom-views/create-view#subclassview).
  // Heuristic to check that the code in the file uses android.util.AttributeSet
  if (viewProvider.document?.charsSequence?.contains("AttributeSet") == false) {
    return false
  }

  return when (this) {
    is PsiClassOwner -> this.classes.any { it.extendsView() } // Properly detect inheritance from View in Smart mode
    else -> false
  }
}

internal fun FileEditor.getCustomViewPreviewManager(): CustomViewPreviewManager? = when(this) {
  is MultiRepresentationPreview -> this.currentRepresentation as? CustomViewPreviewManager
  else -> null
}
