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
import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_FONT_SCALE
import com.android.tools.idea.compose.preview.PARAMETER_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_HEIGHT_DP
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.compose.preview.PARAMETER_WIDTH
import com.android.tools.idea.compose.preview.PARAMETER_WIDTH_DP
import com.android.tools.idea.compose.preview.findPreviewDefaultValues
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.PsiCallEnumSupportValuesProvider
import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.IntegerNormalValidator
import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.IntegerStrictValidator
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.UNDEFINED_API_LEVEL
import com.android.tools.idea.compose.preview.util.UNDEFINED_DIMENSION
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

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
class PsiCallPropertyModel internal constructor(
  private val project: Project,
  resolvedCall: ResolvedCall<*>,
  defaultValues: Map<String, String?>,
  override val enumSupportValuesProvider: EnumSupportValuesProvider
) : PsiPropertyModel() {
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
    fun fromPreviewElement(project: Project, previewElement: PreviewElement): PsiCallPropertyModel {
      val annotationEntry = previewElement.previewElementDefinitionPsi?.element as? KtAnnotationEntry
      val resolvedCall = annotationEntry?.getResolvedCall(annotationEntry.analyze(BodyResolveMode.FULL))!!
      val defaultValues: Map<String, String?> = (annotationEntry.toUElement() as? UAnnotation)?.findPreviewDefaultValues() ?: kotlin.run {
        Logger.getInstance(PsiCallPropertyModel::class.java).warn("Could not obtain default values")
        emptyMap()
      }
      val containingFile = previewElement.previewElementDefinitionPsi?.containingFile
      val module = containingFile?.module

      val valuesProvider = module?.let {
        PsiCallEnumSupportValuesProvider.createPreviewValuesProvider(it, previewElement.composeLibraryNamespace, containingFile.virtualFile)
      } ?: EnumSupportValuesProvider.EMPTY
      return PsiCallPropertyModel(project, resolvedCall, defaultValues.toReadable(), valuesProvider)
    }
  }
}

/**
 * Given a resolved call, this method returns the collection of editable [PsiPropertyItem]s.
 */
private fun parserResolvedCallToPsiPropertyItems(
  project: Project,
  model: PsiCallPropertyModel,
  resolvedCall: ResolvedCall<*>,
  defaultValues: Map<String, String?>
): Collection<PsiPropertyItem> =
  ReadAction.compute<Collection<PsiPropertyItem>, Throwable> {
    return@compute resolvedCall.valueArguments.toList().sortedBy { (descriptor, _) ->
      descriptor.index
    }.map { (descriptor, resolved) ->
      val argumentExpression = (resolved as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression()
      val defaultValue = defaultValues[descriptor.name.asString()]
      when (descriptor.name.asString()) {
        PARAMETER_FONT_SCALE -> FloatPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
        PARAMETER_BACKGROUND_COLOR -> ColorPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
        PARAMETER_WIDTH,
        PARAMETER_WIDTH_DP,
        PARAMETER_HEIGHT,
        PARAMETER_HEIGHT_DP ->
          PsiCallParameterPropertyItem(project, model, resolvedCall, descriptor, argumentExpression, defaultValue, IntegerNormalValidator)
        PARAMETER_API_LEVEL ->
          PsiCallParameterPropertyItem(project, model, resolvedCall, descriptor, argumentExpression, defaultValue, IntegerStrictValidator)
        PARAMETER_UI_MODE,
        PARAMETER_DEVICE -> ClassPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
        else -> PsiCallParameterPropertyItem(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
      }
    }
  }

/**
 * Get the default values from the [PreviewElement] in a format that's easier to understand for the [PsiPropertyItem]s.
 */
private fun Map<String, String?>.toReadable(): Map<String, String?> = this.mapValues { entry ->
  when (entry.key) {
    PARAMETER_API_LEVEL -> entry.value?.apiToReadable()
    PARAMETER_WIDTH,
    PARAMETER_WIDTH_DP,
    PARAMETER_HEIGHT,
    PARAMETER_HEIGHT_DP -> entry.value?.sizeToReadable()
    PARAMETER_BACKGROUND_COLOR -> null // We ignore background color, as the default value is set by Studio
    // TODO: Combobox currently doesn't support empty default value, so we set a text, that will fallback to the desired default option.
    PARAMETER_DEVICE -> entry.value ?: " "
    else -> entry.value
  }
}

private fun String.sizeToReadable(): String? = this.takeIf { it.toInt() != UNDEFINED_DIMENSION }?.toString()

private fun String.apiToReadable(): String? = this.takeIf { it.toInt() != UNDEFINED_API_LEVEL }?.toString()