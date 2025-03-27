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

import com.android.tools.idea.editing.documentation.AndroidJavaDocExternalFilter.Companion.filterTo as androidJavaDocExternalFilterTo
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import java.io.BufferedReader

/** A [DocumentationTarget] representing a class in the Android SDK. */
internal class AndroidSdkClassDocumentationTarget
private constructor(
  targetElement: PsiClass,
  sourceElement: PsiElement?,
  url: String,
  localJavaDocInfo: String?,
) : AndroidSdkDocumentationTarget<PsiClass>(targetElement, sourceElement, url, localJavaDocInfo) {

  override val displayName = targetElement.qualifiedName

  override fun create(
    targetElement: PsiClass,
    sourceElement: PsiElement?,
    url: String,
    localJavaDocInfo: String?,
  ) = AndroidSdkClassDocumentationTarget(targetElement, sourceElement, url, localJavaDocInfo)

  override fun filter(reader: BufferedReader, stringBuilder: StringBuilder) {
    reader.androidJavaDocExternalFilterTo(stringBuilder)
  }

  companion object {
    /**
     * Creates [DocumentationTarget] representing a class in the Android SDK. [targetElement] points
     * to the class in the Android SDK that needs documentation, and [sourceElement] represents the
     * original reference to that class from which the user is requesting the documentation.
     */
    fun create(targetElement: PsiClass, sourceElement: PsiElement?): DocumentationTarget? {
      val url = targetElement.documentationUrl() ?: return null

      val localJavaDocInfo =
        JavaDocInfoGenerator(targetElement.project, targetElement).generateDocInfo(null)

      return AndroidSdkClassDocumentationTarget(targetElement, sourceElement, url, localJavaDocInfo)
    }
  }
}
