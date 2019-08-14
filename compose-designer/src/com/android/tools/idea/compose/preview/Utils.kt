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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.text.nullize
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.UastVisitor

const val UNDEFINED_API_LEVEL = -1
const val UNDEFINED_DIMENSION = -1

data class PreviewConfiguration(val apiLevel: Int,
                                val theme: String?,
                                val width: Int,
                                val height: Int) {
  fun applyTo(renderConfiguration: Configuration) {
    if (apiLevel != UNDEFINED_API_LEVEL) {
      val highestTarget = renderConfiguration.configurationManager.highestApiTarget!!

      renderConfiguration.target = CompatibilityRenderTarget(highestTarget, apiLevel, null)
    }

    if (theme != null) {
      renderConfiguration.setTheme(theme)
    }
  }
}
data class PreviewElement(val name: String, val method: String, val configuration: PreviewConfiguration)

interface PreviewElementFinder {
  /**
   * Returns all the [PreviewElement]s present in the passed [VirtualFile]
   */
  fun findPreviewMethods(project: Project, vFile: VirtualFile): Set<PreviewElement> {
    val uFile: UFile = PsiManager.getInstance(project).findFile(vFile)?.toUElement() as? UFile ?: return emptySet<PreviewElement>()

    return findPreviewMethods(uFile)
  }

  /**
   * Returns all the [PreviewElement]s present in the passed [UFile]
   */
  fun findPreviewMethods(uFile: UFile): Set<PreviewElement>

  /**
   * Returns whether the given [UElement] belongs to a PreviewElement handled by this [PreviewElementFinder]. Implementations must return
   * true if they can not determine if the element belongs to a [PreviewElement] or not.
   * This method will be called to detect changes into [PreviewElement]s and issue a refresh.
   */
  fun elementBelongsToPreviewElement(uElement: UElement): Boolean = true
}