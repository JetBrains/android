/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.idea.compose.preview.PARAMETER_API_LEVEL
import com.android.tools.idea.compose.preview.PARAMETER_BACKGROUND_COLOR
import com.android.tools.idea.compose.preview.PARAMETER_FONT_SCALE
import com.android.tools.idea.compose.preview.PARAMETER_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_BACKGROUND
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_DECORATION
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_SYSTEM_UI
import com.android.tools.idea.compose.preview.PARAMETER_WIDTH
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * [PsiPropertyModel] for pickers handling calls. This is common in Compose where most pickers interact with method calls.
 *
 * For example, a theme in Compose is a method call. This model allows editing those properties.
 * ```
 * val darkThemePalette = themePalette(
 *   colorPrimary = Color(12, 13, 14)
 * )
 * ```
 *
 * The same applies for annotations, where the parameters are considered by the PSI parsing as a method call.
 * ```
 * @Preview(name = "Hello", group = "group")
 * ```
 *
 * In both cases, this [PsiCallPropertyModel] will deal with the named parameters as properties.
 */
class PsiCallPropertyModel internal constructor(private val project: Project,
                                                resolvedCall: ResolvedCall<*>,
                                                defaultValues: Map<String, String?>) : PsiPropertyModel() {
  private val psiPropertiesCollection = parserResolvedCallToPsiPropertyItems(project, this, resolvedCall, defaultValues)

  val psiFactory: KtPsiFactory by lazy { KtPsiFactory(project, true) }

  val ktFile = resolvedCall.call.callElement.containingKtFile

  override val properties: PropertiesTable<PsiPropertyItem> = PropertiesTable.create(
    HashBasedTable.create<String, String, PsiPropertyItem>().also { table ->
      psiPropertiesCollection.forEach {
        table.put(it.namespace, it.name, it)
      }
    })

  companion object {
    fun fromPreviewElement(project: Project,
                           previewElement: PreviewElement): PsiCallPropertyModel {
      val annotationEntry = previewElement.previewElementDefinitionPsi?.element as? KtAnnotationEntry
      val resolvedCall = annotationEntry?.getResolvedCall(annotationEntry.analyze(BodyResolveMode.FULL))!!
      return PsiCallPropertyModel(project, resolvedCall, previewElement.getDefaultValues())
    }
  }
}

/**
 * Given a resolved call, this method returns the collection of editable [PsiPropertyItem]s.
 */
private fun parserResolvedCallToPsiPropertyItems(project: Project,
                                                 model: PsiCallPropertyModel,
                                                 resolvedCall: ResolvedCall<*>,
                                                 defaultValues: Map<String, String?>): Collection<PsiPropertyItem> =
  ReadAction.compute<Collection<PsiPropertyItem>, Throwable> {
    return@compute resolvedCall.valueArguments.map { (descriptor, resolved) ->
      val argumentExpression = (resolved as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression()
      val defaultValue = defaultValues[descriptor.name.asString()]
      when (descriptor.name.asString()) {
        PARAMETER_FONT_SCALE -> FloatPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
        PARAMETER_BACKGROUND_COLOR -> ColorPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
        else -> ClassPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
      }
    }
  }

/**
 * Get the default values from the [PreviewElement] in a format that's easier to understand for the [PsiPropertyItem]s.
 */
private fun PreviewElement.getDefaultValues(): Map<String, String?> =
  mapOf(
    PARAMETER_API_LEVEL to configuration.apiLevel.toString(),
    PARAMETER_WIDTH to configuration.width.toString(),
    PARAMETER_HEIGHT to configuration.height.toString(),
    PARAMETER_FONT_SCALE to configuration.fontScale.toString() + "f",
    PARAMETER_SHOW_SYSTEM_UI to displaySettings.showDecoration.toString(),
    PARAMETER_SHOW_DECORATION to displaySettings.showDecoration.toString(),
    PARAMETER_SHOW_BACKGROUND to displaySettings.showBackground.toString(),
    PARAMETER_BACKGROUND_COLOR to displaySettings.backgroundColor?.substringAfter('#')?.let { "0x$it" }
  )