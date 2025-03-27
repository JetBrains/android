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

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import java.io.BufferedReader

/** A [DocumentationTarget] representing a method in the Android SDK. */
internal class AndroidSdkMethodDocumentationTarget
private constructor(
  targetElement: PsiMethod,
  containingClass: PsiClass,
  sourceElement: PsiElement?,
  url: String,
  localJavaDocInfo: String?,
) :
  AndroidSdkMemberDocumentationTarget<PsiMethod>(
    targetElement,
    containingClass,
    sourceElement,
    url,
    localJavaDocInfo,
  ) {

  override fun create(
    targetElement: PsiMethod,
    containingClass: PsiClass,
    sourceElement: PsiElement?,
    url: String,
    localJavaDocInfo: String?,
  ) =
    AndroidSdkMethodDocumentationTarget(
      targetElement,
      containingClass,
      sourceElement,
      url,
      localJavaDocInfo,
    )

  override fun filter(reader: BufferedReader, stringBuilder: StringBuilder) {
    val qName = containingClass.qualifiedName
    val webContent = buildString { super.filter(reader, this) }
    val headerString = buildString {
      append("<h3>")
      DocumentationManagerUtil.createHyperlink(this, qName, qName, false)
      append("</h3>")
    }
    stringBuilder.append(webContent.replace(methodHeadingRegex, headerString))
  }

  companion object {
    /**
     * Creates [DocumentationTarget] representing a method in the Android SDK. [targetElement]
     * points to the method in the Android SDK that needs documentation, and [sourceElement]
     * represents the original reference to that method from which the user is requesting the
     * documentation.
     */
    fun create(targetElement: PsiMethod, sourceElement: PsiElement?): DocumentationTarget? {
      val containingClass = targetElement.containingClass ?: return null
      val classUrl = containingClass.documentationUrl() ?: return null
      val types =
        targetElement.getSignature(PsiSubstitutor.EMPTY).parameterTypes.joinToString {
          it.canonicalText
        }
      val url = "$classUrl#${targetElement.name}($types)"

      val localJavaDocInfo =
        JavaDocInfoGenerator(targetElement.project, targetElement).generateDocInfo(listOf(url))

      return AndroidSdkMethodDocumentationTarget(
        targetElement,
        containingClass,
        sourceElement,
        url,
        localJavaDocInfo,
      )
    }

    private val methodHeadingRegex =
      Regex("<H[34].*>(.+?)</H[34]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
  }
}
