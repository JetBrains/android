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
package com.android.tools.idea.lang

import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.codeInsight.InferredAnnotationProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiUtil

/**
 * Certain Android SDK annotations are available only in the stub jar (android.jar) and not in attached sources
 * because they are injected by Metalava at build time. This discrepancy confuses certain IntelliJ inspections
 * which search for annotations in sources instead of bytecode (presumably to handle annotations with source retention).
 * As a workaround, this class "infers" additional annotations for Android SDK source elements, matching android.jar.
 * Most notably, this includes nullability annotations.
 */
class AndroidSdkInferredAnnotationProvider(private val project: Project) : InferredAnnotationProvider {

  override fun findInferredAnnotation(owner: PsiModifierListOwner, annotationFQN: String): PsiAnnotation? {
    // Note: the order of conditionals matters for performance.
    if (isAndroidAnnotation(annotationFQN) && isAndroidProject() && isFromJavaPackage(owner)) {
      val compiledElement = PsiUtil.preferCompiledElement(owner)
      if (compiledElement !== owner && compiledElement.hasAnnotation(annotationFQN) && !owner.hasAnnotation(annotationFQN)) {
        return compiledElement.getAnnotation(annotationFQN)
      }
    }
    return null
  }

  override fun findInferredAnnotations(owner: PsiModifierListOwner): List<PsiAnnotation> {
    // Note: the order of conditionals matters for performance.
    if (isAndroidProject() && isFromJavaPackage(owner)) {
      val compiledElement = PsiUtil.preferCompiledElement(owner)
      if (compiledElement !== owner) {
        return compiledElement.annotations.filter { annotation ->
          val fqName = annotation.qualifiedName
          fqName != null && isAndroidAnnotation(fqName) && !owner.hasAnnotation(fqName)
        }
      }
    }
    return emptyList()
  }

  // Android SDK sources are missing annotations only in the 'java' package; these classes are annotated
  // externally to avoid carrying patches against upstream sources. For details see:
  // https://cs.android.com/android/platform/superproject/main/+/main:libcore/ojluni/annotations
  private fun isFromJavaPackage(e: PsiElement): Boolean {
    // Implementation inspired by PsiUtil.isFromDefaultPackage().
    val file = e.containingFile
    if (file !is PsiClassOwner) return false
    val packageName = file.packageName
    return packageName != null && (packageName == "java" || packageName.startsWith("java."))
  }

  // We assume Metalava-injected annotations are always in the Android namespace. This assumption improves performance
  // in the common case where findInferredAnnotation() is searching for an annotation unrelated to the Android SDK.
  private fun isAndroidAnnotation(fqName: String): Boolean {
    return fqName.startsWith("android.") || fqName.startsWith("androidx.") || fqName.startsWith("com.android.")
  }

  private fun isAndroidProject(): Boolean = CommonAndroidUtil.getInstance().isAndroidProject(project)
}
