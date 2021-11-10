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
import com.android.tools.idea.compose.preview.PARAMETER_LOCALE
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.compose.preview.PARAMETER_WIDTH
import com.android.tools.idea.compose.preview.PARAMETER_WIDTH_DP
import com.android.tools.idea.compose.preview.findPreviewDefaultValues
import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.IntegerNormalValidator
import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.IntegerStrictValidator
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.UiMode
import com.android.tools.idea.compose.preview.pickers.tracking.NoOpTracker
import com.android.tools.idea.compose.preview.pickers.tracking.PreviewPickerTracker
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.UNDEFINED_API_LEVEL
import com.android.tools.idea.compose.preview.util.UNDEFINED_DIMENSION
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.caches.resolve.analyze
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
internal class PsiCallPropertyModel internal constructor(
  val project: Project,
  val module: Module,
  resolvedCall: ResolvedCall<*>,
  defaultValues: Map<String, String?>,
  override val tracker: PreviewPickerTracker
) : PsiPropertyModel(), DataProvider {
  private val psiPropertiesCollection = parserResolvedCallToPsiPropertyItems(project, this, resolvedCall, defaultValues)

  val psiFactory: KtPsiFactory by lazy { KtPsiFactory(project, true) }

  val ktFile = resolvedCall.call.callElement.containingKtFile

  override val properties: PropertiesTable<PsiPropertyItem> = PropertiesTable.create(
    HashBasedTable.create<String, String, PsiPropertyItem>().also { table ->
      psiPropertiesCollection.forEach {
        table.put(it.namespace, it.name, it)
      }
    })

  override fun getData(dataId: String): Any? =
    when(dataId) {
      // TODO: Implement to provide data required by some properties, eg: available devices
      else -> null
    }

  companion object {
    fun fromPreviewElement(
      project: Project,
      module: Module,
      previewElement: PreviewElement,
      tracker: PreviewPickerTracker
    ): PsiCallPropertyModel {
      val annotationEntry = previewElement.previewElementDefinitionPsi?.element as? KtAnnotationEntry
      val resolvedCall = annotationEntry?.getResolvedCall(annotationEntry.analyze(BodyResolveMode.FULL))!!
      val libraryDefaultValues: Map<String, String?> =
        (annotationEntry.toUElement() as? UAnnotation)?.findPreviewDefaultValues() ?: kotlin.run {
          Logger.getInstance(PsiCallPropertyModel::class.java).warn("Could not obtain default values")
          emptyMap()
        }
      val defaultApiLevel = ConfigurationManager.findExistingInstance(module)?.defaultTarget?.version?.apiLevel?.toString()

      /**
       * Contains the default values for each parameter of the Preview annotation.
       *
       * This either makes the existing default values of the @Preview Class presentable, or changes the value based on what the value
       * actually represents on the preview.
       */
      val defaultValues = libraryDefaultValues.mapValues { entry ->
          when (entry.key) {
            PARAMETER_API_LEVEL -> entry.value?.apiToReadable() ?: defaultApiLevel
            PARAMETER_WIDTH,
            PARAMETER_WIDTH_DP,
            PARAMETER_HEIGHT,
            PARAMETER_HEIGHT_DP -> entry.value?.sizeToReadable()
            PARAMETER_BACKGROUND_COLOR -> null // We ignore background color, as the default value is set by Studio
            PARAMETER_UI_MODE -> UiMode.values().firstOrNull { it.resolvedValue == entry.value }?.display ?: "Unknown"
            PARAMETER_DEVICE -> entry.value ?: "Default"
            PARAMETER_LOCALE -> entry.value ?: "Default (en-US)"
            else -> entry.value
          }
        }

      return PsiCallPropertyModel(project, module, resolvedCall, defaultValues, tracker)
    }

    private fun String.sizeToReadable(): String? = this.takeIf { it.toInt() != UNDEFINED_DIMENSION }?.toString()

    private fun String.apiToReadable(): String? = this.takeIf { it.toInt() != UNDEFINED_API_LEVEL }?.toString()
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
): Collection<PsiPropertyItem> {
  val properties = mutableListOf<PsiPropertyItem>()
  ReadAction.run<Throwable> {
    resolvedCall.valueArguments.toList().sortedBy { (descriptor, _) ->
      descriptor.index
    }.forEach { (descriptor, resolved) ->
      val argumentExpression = (resolved as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression()
      val defaultValue = defaultValues[descriptor.name.asString()]
      when (descriptor.name.asString()) {
        // TODO(b/197021783): Capitalize the displayed name of the parameters, without affecting the output of the model or hardcoding the names
        PARAMETER_FONT_SCALE -> FloatPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
        PARAMETER_BACKGROUND_COLOR -> ColorPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
        PARAMETER_WIDTH,
        PARAMETER_WIDTH_DP,
        PARAMETER_HEIGHT,
        PARAMETER_HEIGHT_DP ->
          PsiCallParameterPropertyItem(project, model, resolvedCall, descriptor, argumentExpression, defaultValue, IntegerNormalValidator)
        PARAMETER_API_LEVEL ->
          PsiCallParameterPropertyItem(project, model, resolvedCall, descriptor, argumentExpression, defaultValue, IntegerStrictValidator)
        PARAMETER_DEVICE -> {
          // Note that DeviceParameterPropertyItem sets its own name to PARAMETER_HARDWARE_DEVICE
          DeviceParameterPropertyItem(project, model, resolvedCall, descriptor, argumentExpression, defaultValue).also {
            properties.addAll(it.innerProperties)
          }
        }
        PARAMETER_UI_MODE -> ClassPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
        else -> PsiCallParameterPropertyItem(project, model, resolvedCall, descriptor, argumentExpression, defaultValue)
      }.also {
        properties.add(it)
      }
    }
  }
  return properties
}