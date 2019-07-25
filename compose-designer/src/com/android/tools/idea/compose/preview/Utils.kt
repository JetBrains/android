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

import com.android.resources.ScreenOrientation
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
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.UastVisitor

const val UNDEFINED_API_LEVEL = -1

data class PreviewConfiguration(val apiLevel: Int?,
                                val theme: String?,
                                val orientation: ScreenOrientation?) {
  fun applyTo(renderConfiguration: Configuration) {
    if (apiLevel != null && apiLevel != UNDEFINED_API_LEVEL) {
      val highestTarget = renderConfiguration.configurationManager.highestApiTarget!!

      renderConfiguration.target = CompatibilityRenderTarget(highestTarget, apiLevel, null)
    }

    if (theme != null) {
      renderConfiguration.setTheme(theme)
    }

    if (orientation != null && orientation != renderConfiguration.deviceState?.orientation) {
      renderConfiguration
        .device
        ?.allStates
        ?.find { it.orientation == orientation }
        ?.let {
          renderConfiguration.deviceState = it
        }
    }
  }
}
data class PreviewElement(val name: String, val method: String, val configuration: PreviewConfiguration)

interface PreviewElementFinder {
  fun findPreviewMethods(project: Project, vFile: VirtualFile): Set<PreviewElement> {
    val uFile: UFile = PsiManager.getInstance(project).findFile(vFile)?.toUElement() as? UFile ?: return emptySet<PreviewElement>()

    return findPreviewMethods(uFile)
  }

  fun findPreviewMethods(uFile: UFile): Set<PreviewElement>
}

/**
 * [PreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationPreviewElementFinder : PreviewElementFinder {
  /**
   * Reads the `@Preview` annotation parameters and returns a [PreviewConfiguration] containing the values.
   */
  private fun attributesToConfiguration(node: UAnnotation): PreviewConfiguration {
    val apiLevel = node.findAttributeValue("apiLevel")?.evaluate() as? Int
    val theme = node.findAttributeValue("theme")?.evaluateString()?.nullize()
    val orientation = when (node.findAttributeValue("orientation")?.asRenderString()) {
      "Orientation.LANDSCAPE" -> ScreenOrientation.LANDSCAPE
      null -> null
      else -> ScreenOrientation.PORTRAIT
    }

    return PreviewConfiguration(apiLevel, theme, orientation)
  }

  /**
   * Returns all the `@Composable` methods in the [uFile] that are also tagged with `@Preview`.
   */
  override fun findPreviewMethods(uFile: UFile): Set<PreviewElement> {
    val previewMethodsFqNames = mutableSetOf<PreviewElement>()
    uFile.accept(object : UastVisitor {
      private var stopVisitor = false

      override fun visitElement(node: UElement): Boolean = stopVisitor

      /**
       * Called for every `@Preview` annotation.
       */
      private fun visitPreviewAnnotation(previewAnnotation: UAnnotation) {
        val uMethod = previewAnnotation.uastParent as UMethod

        if (!uMethod.parameterList.isEmpty) {
          error("Preview methods must not have any parameters")
        }

        val uClass: UClass = uMethod.uastParent as UClass
        val composableMethod = "${uClass.qualifiedName}.${uMethod.name}"
        val previewName = previewAnnotation.findAttributeValue("name")?.evaluateString() ?: ""

        previewMethodsFqNames.add(PreviewElement(previewName, composableMethod,
                                                 attributesToConfiguration(previewAnnotation)))
      }

      override fun visitAnnotation(node: UAnnotation): Boolean {
        if (PREVIEW_ANNOTATION == node.qualifiedName) {
          visitPreviewAnnotation(node)
        }

        return super.visitAnnotation(node)
      }
    })

    return previewMethodsFqNames
  }
}