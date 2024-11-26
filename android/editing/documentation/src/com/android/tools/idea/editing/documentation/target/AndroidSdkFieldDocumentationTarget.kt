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
package com.android.tools.idea.editing.documentation.target

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField

/** A [DocumentationTarget] representing a field in the Android SDK. */
internal class AndroidSdkFieldDocumentationTarget
private constructor(
  targetElement: PsiField,
  containingClass: PsiClass,
  sourceElement: PsiElement?,
  url: String,
  localJavaDocInfo: String?,
) :
  AndroidSdkMemberDocumentationTarget<PsiField>(
    targetElement,
    containingClass,
    sourceElement,
    url,
    localJavaDocInfo,
  ) {
  override fun create(
    targetElement: PsiField,
    containingClass: PsiClass,
    sourceElement: PsiElement?,
    url: String,
    localJavaDocInfo: String?,
  ) =
    AndroidSdkFieldDocumentationTarget(
      targetElement,
      containingClass,
      sourceElement,
      url,
      localJavaDocInfo,
    )

  companion object {
    /**
     * Creates [DocumentationTarget] representing a field in the Android SDK. [targetElement] points
     * to the field in the Android SDK that needs documentation, and [sourceElement] represents the
     * original reference to that field from which the user is requesting the documentation.
     */
    fun create(targetElement: PsiField, sourceElement: PsiElement?): DocumentationTarget? {
      val containingClass = targetElement.containingClass ?: return null
      val classUrl = containingClass.documentationUrl() ?: return null
      val url = "$classUrl#${targetElement.name}"

      val localJavaDocInfo =
        JavaDocInfoGenerator(targetElement.project, targetElement).generateDocInfo(listOf(url))

      return AndroidSdkFieldDocumentationTarget(
        targetElement,
        containingClass,
        sourceElement,
        url,
        localJavaDocInfo,
      )
    }
  }
}
