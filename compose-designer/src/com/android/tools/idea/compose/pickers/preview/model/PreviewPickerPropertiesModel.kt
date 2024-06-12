/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.preview.model

import com.android.tools.idea.compose.pickers.base.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.pickers.base.inspector.PsiPropertiesInspectorBuilder
import com.android.tools.idea.compose.pickers.base.model.PsiCallPropertiesModel
import com.android.tools.idea.compose.pickers.base.model.PsiPropertiesProvider
import com.android.tools.idea.compose.pickers.base.property.PsiCallParameterPropertyItem
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.base.tracking.ComposePickerTracker
import com.android.tools.idea.compose.pickers.common.editingsupport.IntegerNormalValidator
import com.android.tools.idea.compose.pickers.common.editingsupport.IntegerStrictValidator
import com.android.tools.idea.compose.pickers.common.property.BooleanPsiCallParameter
import com.android.tools.idea.compose.pickers.common.property.ClassPsiCallParameter
import com.android.tools.idea.compose.pickers.common.property.ColorPsiCallParameter
import com.android.tools.idea.compose.pickers.common.property.FloatPsiCallParameter
import com.android.tools.idea.compose.pickers.preview.enumsupport.PreviewPickerValuesProvider
import com.android.tools.idea.compose.pickers.preview.enumsupport.UiMode
import com.android.tools.idea.compose.pickers.preview.enumsupport.Wallpaper
import com.android.tools.idea.compose.pickers.preview.inspector.PreviewPropertiesInspectorBuilder
import com.android.tools.idea.compose.pickers.preview.property.DeviceParameterPropertyItem
import com.android.tools.idea.compose.pickers.preview.utils.addNewValueArgument
import com.android.tools.idea.compose.pickers.preview.utils.getArgumentForParameter
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.preview.findPreviewDefaultValues
import com.android.tools.idea.preview.util.AvailableDevicesKey
import com.android.tools.idea.preview.util.getSdkDevices
import com.android.tools.preview.UNDEFINED_API_LEVEL
import com.android.tools.preview.UNDEFINED_DIMENSION
import com.android.tools.preview.config.PARAMETER_API_LEVEL
import com.android.tools.preview.config.PARAMETER_BACKGROUND_COLOR
import com.android.tools.preview.config.PARAMETER_DEVICE
import com.android.tools.preview.config.PARAMETER_FONT_SCALE
import com.android.tools.preview.config.PARAMETER_HARDWARE_DEVICE
import com.android.tools.preview.config.PARAMETER_HEIGHT
import com.android.tools.preview.config.PARAMETER_HEIGHT_DP
import com.android.tools.preview.config.PARAMETER_LOCALE
import com.android.tools.preview.config.PARAMETER_SHOW_BACKGROUND
import com.android.tools.preview.config.PARAMETER_SHOW_SYSTEM_UI
import com.android.tools.preview.config.PARAMETER_UI_MODE
import com.android.tools.preview.config.PARAMETER_WALLPAPER
import com.android.tools.preview.config.PARAMETER_WIDTH
import com.android.tools.preview.config.PARAMETER_WIDTH_DP
import com.android.tools.preview.config.findOrParseFromDefinition
import com.android.tools.preview.config.getDefaultPreviewDevice
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.caches.resolve.analyze as analyzeK1
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

/** The model for pickers that handles calls to the Preview annotation in Compose. */
internal class PreviewPickerPropertiesModel
private constructor(
  project: Project,
  module: Module,
  ktFile: KtFile,
  psiPropertiesProvider: PreviewPropertiesProvider,
  valuesProvider: EnumSupportValuesProvider,
  tracker: ComposePickerTracker,
) :
  PsiCallPropertiesModel(
    project = project,
    module = module,
    ktFile = ktFile,
    psiPropertiesProvider = psiPropertiesProvider,
    tracker = tracker,
  ) {

  override val inspectorBuilder: PsiPropertiesInspectorBuilder =
    PreviewPropertiesInspectorBuilder(valuesProvider)

  private val availableDevices = getSdkDevices(module)

  override fun getData(dataId: String): Any? =
    when (dataId) {
      CurrentDeviceKey.name -> {
        val currentDeviceValue = properties.getOrNull("", PARAMETER_HARDWARE_DEVICE)?.value
        val deviceFromParameterValue =
          currentDeviceValue?.let(availableDevices::findOrParseFromDefinition)

        deviceFromParameterValue
          ?: ConfigurationManager.findExistingInstance(module)?.getDefaultPreviewDevice()
      }
      AvailableDevicesKey.name -> {
        availableDevices
      }
      else -> null
    }

  companion object {
    fun fromPreviewElement(
      project: Project,
      module: Module,
      previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
      tracker: ComposePickerTracker,
    ): PreviewPickerPropertiesModel {
      val annotationEntry = previewElementDefinitionPsi?.element as? KtAnnotationEntry
      val libraryDefaultValues: Map<String, String?> =
        (annotationEntry.toUElement() as? UAnnotation)?.findPreviewDefaultValues()
          ?: kotlin.run {
            Logger.getInstance(PsiCallPropertiesModel::class.java)
              .warn("Could not obtain default values")
            emptyMap()
          }
      val valuesProvider =
        PreviewPickerValuesProvider.createPreviewValuesProvider(
          module = module,
          containingFile = previewElementDefinitionPsi?.virtualFile,
        )
      val defaultApiLevel =
        ConfigurationManager.findExistingInstance(module)
          ?.defaultTarget
          ?.version
          ?.apiLevel
          ?.toString()

      /**
       * Contains the default values for each parameter of the Preview annotation.
       *
       * This either makes the existing default values of the @Preview Class presentable, or changes
       * the value based on what the value actually represents on the preview.
       */
      val defaultValues =
        libraryDefaultValues.mapValues { entry ->
          when (entry.key) {
            PARAMETER_API_LEVEL -> entry.value?.apiToReadable() ?: defaultApiLevel
            PARAMETER_WIDTH,
            PARAMETER_WIDTH_DP,
            PARAMETER_HEIGHT,
            PARAMETER_HEIGHT_DP -> entry.value?.sizeToReadable()
            PARAMETER_BACKGROUND_COLOR ->
              null // We ignore background color, as the default value is set by Studio
            PARAMETER_UI_MODE ->
              UiMode.values().firstOrNull { it.resolvedValue == entry.value }?.display ?: "Unknown"
            PARAMETER_DEVICE -> entry.value ?: "Default"
            PARAMETER_LOCALE -> entry.value ?: "Default (en-US)"
            PARAMETER_WALLPAPER ->
              Wallpaper.values().firstOrNull { it.resolvedValue == entry.value }?.display ?: "None"
            PARAMETER_FONT_SCALE -> entry.value?.removeSuffix("f")
            else -> entry.value
          }
        }

      if (annotationEntry == null) {
        Logger.getInstance(PsiCallPropertiesModel::class.java)
          .error("Non-null value is expected for annotation entry")
      }

      return PreviewPickerPropertiesModel(
        project = project,
        module = module,
        ktFile = annotationEntry?.containingKtFile!!,
        psiPropertiesProvider = PreviewPropertiesProvider(defaultValues, annotationEntry),
        tracker = tracker,
        valuesProvider = valuesProvider,
      )
    }

    private fun String.sizeToReadable(): String? =
      this.takeIf { it.toInt() != UNDEFINED_DIMENSION }?.toString()

    private fun String.apiToReadable(): String? =
      this.takeIf { it.toInt() != UNDEFINED_API_LEVEL }?.toString()
  }
}

/**
 * [PsiPropertiesProvider] for the Preview annotation. Provides specific implementations for known
 * parameters of the annotation.
 */
private class PreviewPropertiesProvider(
  private val defaultValues: Map<String, String?>,
  private val annotationEntry: KtAnnotationEntry,
) : PsiPropertiesProvider {
  val resolvedCall: ResolvedCall<out CallableDescriptor>? =
    if (KotlinPluginModeProvider.isK2Mode()) null
    else annotationEntry.getResolvedCall(annotationEntry.analyzeK1(BodyResolveMode.FULL))

  override fun invoke(
    project: Project,
    model: PsiCallPropertiesModel,
  ): Collection<PsiPropertyItem> {
    val properties = mutableListOf<PsiPropertyItem>()
    ReadAction.run<Throwable> {
      if (KotlinPluginModeProvider.isK2Mode()) {
        collectParameterPropertyItemsForK2(project, model, properties)
        return@run
      }
      resolvedCall!!
        .valueArguments
        .toList()
        .sortedBy { (descriptor, _) -> descriptor.index }
        .forEach { (descriptor, resolved) ->
          val argumentExpression =
            (resolved as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression()
          val defaultValue = defaultValues[descriptor.name.asString()]
          val parameterName = descriptor.name
          val parameterTypeNameIfStandard = descriptor.type.nameIfStandardType
          collectParameterPropertyItems(
            project,
            model,
            properties,
            parameterName,
            parameterTypeNameIfStandard,
            argumentExpression,
            defaultValue,
            null,
          )
        }
    }
    return properties
  }

  private fun collectParameterPropertyItems(
    project: Project,
    model: PsiCallPropertiesModel,
    properties: MutableCollection<PsiPropertyItem>,
    parameterName: Name,
    parameterTypeNameIfStandard: Name?,
    argumentExpression: KtExpression?,
    defaultValue: String?,
    callElement: KtCallElement?,
  ) {
    fun addNewValueArgument(newValueArgument: KtValueArgument, psiFactory: KtPsiFactory) =
      if (KotlinPluginModeProvider.isK2Mode()) {
        callElement?.addNewValueArgument(newValueArgument, psiFactory)
      } else {
        resolvedCall?.addNewValueArgument(newValueArgument, psiFactory)
      }

    when (
      parameterName.asString()
    ) { // TODO(b/197021783): Capitalize the displayed name of the parameters, without affecting
      // the output of the model or hardcoding the names
      PARAMETER_FONT_SCALE ->
        FloatPsiCallParameter(
          project,
          model,
          ::addNewValueArgument,
          parameterName,
          parameterTypeNameIfStandard,
          argumentExpression,
          defaultValue,
        )
      PARAMETER_BACKGROUND_COLOR ->
        ColorPsiCallParameter(
          project,
          model,
          ::addNewValueArgument,
          parameterName,
          parameterTypeNameIfStandard,
          argumentExpression,
          defaultValue,
        )
      PARAMETER_WIDTH,
      PARAMETER_WIDTH_DP,
      PARAMETER_HEIGHT,
      PARAMETER_HEIGHT_DP ->
        PsiCallParameterPropertyItem(
          project,
          model,
          ::addNewValueArgument,
          parameterName,
          parameterTypeNameIfStandard,
          argumentExpression,
          defaultValue,
          IntegerNormalValidator,
        )
      PARAMETER_API_LEVEL ->
        PsiCallParameterPropertyItem(
          project,
          model,
          ::addNewValueArgument,
          parameterName,
          parameterTypeNameIfStandard,
          argumentExpression,
          defaultValue,
          IntegerStrictValidator,
        )
      PARAMETER_DEVICE -> { // Note that DeviceParameterPropertyItem sets its own name to
        // PARAMETER_HARDWARE_DEVICE
        DeviceParameterPropertyItem(
            project,
            model,
            ::addNewValueArgument,
            parameterName,
            parameterTypeNameIfStandard,
            argumentExpression,
            defaultValue,
          )
          .also { properties.addAll(it.innerProperties) }
      }
      PARAMETER_UI_MODE,
      PARAMETER_WALLPAPER ->
        ClassPsiCallParameter(
          project,
          model,
          ::addNewValueArgument,
          parameterName,
          parameterTypeNameIfStandard,
          argumentExpression,
          defaultValue,
        )
      PARAMETER_SHOW_SYSTEM_UI,
      PARAMETER_SHOW_BACKGROUND ->
        BooleanPsiCallParameter(
          project,
          model,
          ::addNewValueArgument,
          parameterName,
          parameterTypeNameIfStandard,
          argumentExpression,
          defaultValue,
        )
      else ->
        PsiCallParameterPropertyItem(
          project,
          model,
          ::addNewValueArgument,
          parameterName,
          parameterTypeNameIfStandard,
          argumentExpression,
          defaultValue,
        )
    }.also { properties.add(it) }
  }

  @OptIn(KaAllowAnalysisOnEdt::class)
  private fun collectParameterPropertyItemsForK2(
    project: Project,
    model: PsiCallPropertiesModel,
    properties: MutableCollection<PsiPropertyItem>,
  ) = allowAnalysisOnEdt {
    analyze(annotationEntry) {
      val resolvedFunctionCall = annotationEntry.resolveCall()?.singleFunctionCallOrNull() ?: return
      val callableSymbol = resolvedFunctionCall.symbol
      callableSymbol.valueParameters.forEach { parameter ->
        val argument = getArgumentForParameter(resolvedFunctionCall, parameter)
        val defaultValue = defaultValues[parameter.name.asString()]
        collectParameterPropertyItems(
          project,
          model,
          properties,
          parameter.name,
          parameter.returnType.expandedClassSymbol?.name,
          argument,
          defaultValue,
          annotationEntry,
        )
      }
    }
  }
}
