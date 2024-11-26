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
package com.android.tools.idea.editing.documentation

import com.android.SdkConstants
import com.android.tools.idea.editing.documentation.target.AndroidSdkDocumentationTarget
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement

/**
 * A [PsiDocumentationTargetProvider] which can fetch documentation for classes defined in the
 * Android SDK from developer.android.com. This only runs when javadocs are not available from
 * locally-downloaded SDK sources, and only for class documentation (not methods, fields, etc).
 */
class AndroidSdkDocumentationTargetProvider : PsiDocumentationTargetProvider {
  override fun documentationTarget(
    element: PsiElement,
    originalElement: PsiElement?,
  ): DocumentationTarget? {
    if (!element.project.getProjectSystem().isAndroidProject()) return null

    // If sources exist (and therefore have javadocs), the navigation element will not be in
    // android.jar. In that case, fall back to the standard java documentation handler.
    if (!element.navigationElement.isInAndroidSdkJar()) return null

    return AndroidSdkDocumentationTarget.create(element, originalElement)
  }

  private fun PsiElement.isInAndroidSdkJar(): Boolean {
    val virtualFile = containingFile?.virtualFile ?: return false
    return JarFileSystem.getInstance().getVirtualFileForJar(virtualFile)?.name ==
      SdkConstants.FN_FRAMEWORK_LIBRARY
  }
}
