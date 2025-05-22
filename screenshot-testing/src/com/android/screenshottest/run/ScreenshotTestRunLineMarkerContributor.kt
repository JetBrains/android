/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.run

import com.android.tools.idea.projectsystem.isScreenshotTestFile
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.android.tools.idea.flags.StudioFlags
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement
import com.intellij.icons.AllIcons
import org.jetbrains.kotlin.idea.base.util.isUnderKotlinSourceRootTypes

class ScreenshotTestRunLineMarkerContributor: RunLineMarkerContributor() {
  private val annotationsVisited = mutableMapOf<String, Boolean>()
  override fun getInfo(element: PsiElement): Info? {
    return null
  }

  override fun getSlowInfo(element: PsiElement): Info? {
    if (!StudioFlags.ENABLE_SCREENSHOT_TESTING.get() ||
        !isScreenshotTestFile(element.project, element.containingFile.virtualFile)) return null

    val declaration = element.getStrictParentOfType<KtNamedDeclaration>()?.takeIf { it.nameIdentifier == element } ?: return null
    if (isValidKtMethodIdentifier(declaration) || isValidKtTestClassIdentifier(declaration)) {
      return Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions()) { "Run something" }
    }
    return null
  }

  private fun isValidKtTestClassIdentifier(declaration: KtNamedDeclaration): Boolean {
    return declaration is KtClassOrObject &&
           declaration.isUnderKotlinSourceRootTypes() &&
           declaration is KtClass &&
           declaration.declarations.any { it is KtNamedFunction && isPreviewMethod(it) }
  }

  private fun isValidKtMethodIdentifier(declaration: KtNamedDeclaration): Boolean {
    return declaration is KtNamedFunction &&
           declaration.isUnderKotlinSourceRootTypes() &&
           isPreviewMethod(declaration)
  }

  private fun isPreviewMethod(declaration: KtNamedFunction): Boolean {
    return declaration.annotationEntries.any { annotation ->
      (annotation.toUElement() as? UAnnotation)?.javaPsi?.let { isMultiPreview(it, declaration) } ?: false
    }
  }

  private fun isMultiPreview(psiAnnotation: PsiAnnotation, declaration: KtNamedFunction): Boolean {
    val annotationName = psiAnnotation.qualifiedName ?: return false
    if (annotationName == "androidx.compose.ui.tooling.preview.Preview") return true
    if (annotationsVisited.contains(annotationName)) return annotationsVisited[annotationName]!!
    annotationsVisited[annotationName] = false
    val annotationClass = JavaPsiFacade.getInstance(declaration.project)
                            .findClass(annotationName, declaration.resolveScope) ?: return false
    val isMultiPreviewAnnotation = annotationClass.annotations.any {
      isMultiPreview(it, declaration)
    }
    annotationsVisited[annotationName] = isMultiPreviewAnnotation
    return isMultiPreviewAnnotation
  }

}