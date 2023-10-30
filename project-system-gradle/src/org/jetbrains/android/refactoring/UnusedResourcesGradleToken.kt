/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.SdkConstants
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

class UnusedResourcesGradleToken : UnusedResourcesToken<GradleProjectSystem>, GradleToken {
  override fun getPerformerFor(projectSystem: GradleProjectSystem, psiFile: PsiFile): UnusedResourcesPerformer? {
    if (
      (psiFile is GroovyFile || psiFile is KtFile) &&
      (psiFile.name.endsWith(SdkConstants.EXT_GRADLE) ||
       psiFile.name.endsWith(SdkConstants.EXT_GRADLE_KTS))
    ) {
      val buildModel =
        GradleModelProvider.getInstance().parseBuildFile(psiFile.virtualFile, projectSystem.project)
      return GradleUnusedResourcesPerformer(buildModel)
    }
    return null
  }
}

class GradleUnusedResourcesPerformer(private val buildModel: GradleBuildModel) : UnusedResourcesPerformer {

  override fun computeUnusedElements(psiFile: PsiFile, names: Set<String>): Collection<PsiElement> {
    val result = mutableListOf<PsiElement>()

    // Get all the resValue declared within the android block.
    val androidElement = buildModel.android()
    val resValues = buildList {
      addAll(androidElement.defaultConfig().resValues())
      addAll(androidElement.productFlavors().flatMap { it.resValues() })
      addAll(androidElement.buildTypes().flatMap { it.resValues() })
    }

    for (resValue in resValues) {
      val psiElement = resValue.getModel().getPsiElement() ?: continue
      // See if this is one of the unused resources
      val expectedResourceName =
        "${SdkConstants.R_PREFIX}${resValue.type()}.${resValue.name()}"
      if (names.contains(expectedResourceName)) {
        result.add(psiElement)
        resValue.remove()
      }
    }
    return result
  }

  override fun perform(element: PsiElement) {
    if (buildModel.isModified) buildModel.applyChanges()
  }
}