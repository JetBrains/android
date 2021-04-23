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
package com.android.tools.idea.compose.preview

import com.android.tools.compose.COMPOSABLE_FQ_NAMES
import com.android.tools.compose.PREVIEW_ANNOTATION_FQNS
import com.android.tools.compose.PREVIEW_PARAMETER_FQNS
import com.android.tools.compose.findComposeLibraryNamespace
import com.android.tools.idea.compose.preview.util.ParametrizedPreviewElementTemplate
import com.android.tools.idea.compose.preview.util.PreviewConfiguration
import com.android.tools.idea.compose.preview.util.PreviewDisplaySettings
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewParameter
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.android.tools.idea.compose.preview.util.toSmartPsiPointer
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.openapi.application.ReadAction
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.kotlin.KotlinUClassLiteralExpression


/**
 * Returns true if the [KtAnnotationEntry] is a `@Preview` annotation.
 */
internal fun KtAnnotationEntry.isPreviewAnnotation() = ReadAction.compute<Boolean, Throwable> {
  PREVIEW_ANNOTATION_FQNS.contains(getQualifiedName())
}

/**
 * Returns true if the [UAnnotation] is a `@Preview` annotation.
 */
internal fun UAnnotation.isPreviewAnnotation() = ReadAction.compute<Boolean, Throwable> {
  PREVIEW_ANNOTATION_FQNS.contains(qualifiedName)
}

/**
 * Converts the [UAnnotation] to a [PreviewElement] if the annotation is a `@Preview` annotation or returns null
 * if it's not.
 */
internal fun UAnnotation.toPreviewElement(overrideGroupName: String? = null): PreviewElement? = ReadAction.compute<PreviewElement?, Throwable> {
  if (isPreviewAnnotation()) {
    val uMethod = getContainingUMethod()
    uMethod?.let {
      // The method must also be annotated with @Composable
      if (it.uAnnotations.any { annotation -> COMPOSABLE_FQ_NAMES.contains(annotation.qualifiedName) }) {
        return@compute previewAnnotationToPreviewElement(this, it, overrideGroupName)
      }
    }
  }
  return@compute null
}

internal fun UAnnotation.findPreviewDefaultValues(): Map<String, String?> = (this.resolve() as KtLightClass).methods.map { psiMethod ->
  Pair(psiMethod.name, (psiMethod as KtLightMethod).defaultValue?.text?.trim('"')?.nullize())
}.toMap()

private fun UAnnotation.findAttributeIntValue(name: String) =
  findAttributeValue(name)?.evaluate() as? Int

private fun UAnnotation.findAttributeFloatValue(name: String) =
  findAttributeValue(name)?.evaluate() as? Float

private fun UAnnotation.findClassNameValue(name: String) =
  (findAttributeValue(name) as? KotlinUClassLiteralExpression)?.type?.canonicalText

/**
 * Looks up for annotation element using a set of annotation qualified names.
 *
 * @param fqName the qualified name to search
 * @return the first annotation element with the specified qualified name, or null if there is no annotation with such name.
 */
private fun UAnnotated.findAnnotation(fqName: Set<String>): UAnnotation? = uAnnotations.firstOrNull { fqName.contains(it.qualifiedName) }

/**
 * Reads the `@Preview` annotation parameters and returns a [PreviewConfiguration] containing the values.
 */
private fun attributesToConfiguration(node: UAnnotation, defaultValues: Map<String, String?>): PreviewConfiguration {
  val apiLevel = node.getIntAttribute(PARAMETER_API_LEVEL, defaultValues)
  val theme = node.getStringAttribute(PARAMETER_THEME, defaultValues)
  // Both width and height have to support old ("width") and new ("widthDp") conventions
  val width = node.getIntAttribute(PARAMETER_WIDTH, defaultValues) ?: node.getIntAttribute(PARAMETER_WIDTH_DP, defaultValues)
  val height = node.getIntAttribute(PARAMETER_HEIGHT, defaultValues) ?: node.getIntAttribute(PARAMETER_HEIGHT_DP, defaultValues)
  val fontScale = node.getFloatAttribute(PARAMETER_FONT_SCALE, defaultValues)
  val uiMode = node.getIntAttribute(PARAMETER_UI_MODE, defaultValues)
  val device = node.getStringAttribute(PARAMETER_DEVICE, defaultValues)
  val locale = node.getStringAttribute(PARAMETER_LOCALE, defaultValues)

  return PreviewConfiguration.cleanAndGet(apiLevel, theme, width, height, locale, fontScale, uiMode, device)
}

/**
 * Converts the given [previewAnnotation] to a [PreviewElement].
 */
private fun previewAnnotationToPreviewElement(previewAnnotation: UAnnotation,
                                              annotatedMethod: UMethod,
                                              overrideGroupName: String? = null): PreviewElement? {
  val uClass: UClass = annotatedMethod.uastParent as UClass
  val composableMethod = "${uClass.qualifiedName}.${annotatedMethod.name}"
  val previewName = previewAnnotation.findDeclaredAttributeValue(PARAMETER_NAME)?.evaluateString() ?: annotatedMethod.name
  val defaultValues = previewAnnotation.findPreviewDefaultValues()

  fun getBooleanAttribute(attributeName: String) = previewAnnotation.findDeclaredAttributeValue(attributeName)?.evaluate() as? Boolean
                                                   ?: defaultValues[attributeName]?.toBoolean()

  val groupName = overrideGroupName ?: previewAnnotation.findDeclaredAttributeValue(PARAMETER_GROUP)?.evaluateString()
  val showDecorations = getBooleanAttribute(PARAMETER_SHOW_DECORATION) ?: (getBooleanAttribute(PARAMETER_SHOW_SYSTEM_UI)) ?: false
  val showBackground = getBooleanAttribute(PARAMETER_SHOW_BACKGROUND) ?: false
  // We don't use the library's default value for BackgroundColor and instead use a value defined here, see PreviewElement#toPreviewXml.
  val backgroundColor = previewAnnotation.findDeclaredAttributeValue(PARAMETER_BACKGROUND_COLOR)?.evaluate()
  val backgroundColorString = when (backgroundColor) {
    is Int -> backgroundColor.toString(16)
    is Long -> backgroundColor.toString(16)
    else -> null
  }?.let { "#$it" }

  // If the same composable functions is found multiple times, only keep the first one. This usually will happen during
  // copy & paste and both the compiler and Studio will flag it as an error.
  val displaySettings = PreviewDisplaySettings(previewName,
                                               groupName,
                                               showDecorations,
                                               showBackground,
                                               backgroundColorString)

  val parameters = getPreviewParameters(annotatedMethod.uastParameters)
  val composeLibraryNamespace = previewAnnotation.findComposeLibraryNamespace()
  val basePreviewElement = SinglePreviewElementInstance(composableMethod,
                                                        displaySettings,
                                                        previewAnnotation.toSmartPsiPointer(),
                                                        annotatedMethod.uastBody.toSmartPsiPointer(),
                                                        attributesToConfiguration(previewAnnotation, defaultValues),
                                                        composeLibraryNamespace)
  return if (!parameters.isEmpty()) {
    ParametrizedPreviewElementTemplate(basePreviewElement, parameters)
  }
  else {
    basePreviewElement
  }
}

/**
 * Returns a list of [PreviewParameter] for the given [Collection<UParameter>]. If the parameters are annotated with
 * `PreviewParameter`, then they will be returned as part of the collection.
 */
private fun getPreviewParameters(parameters: Collection<UParameter>): Collection<PreviewParameter> =
  parameters.mapIndexedNotNull { index, parameter ->
    val annotation = parameter.findAnnotation(PREVIEW_PARAMETER_FQNS) ?: return@mapIndexedNotNull null
    val providerClassFqn = (annotation.findClassNameValue("provider")) ?: return@mapIndexedNotNull null
    val limit = annotation.findAttributeIntValue("limit") ?: Int.MAX_VALUE
    PreviewParameter(parameter.name, index, providerClassFqn, limit)
  }

private fun UAnnotation.getIntAttribute(attributeName: String, defaultValues: Map<String, String?>) =
  this.findAttributeIntValue(attributeName) ?: defaultValues[attributeName]?.toInt()

private fun UAnnotation.getFloatAttribute(attributeName: String, defaultValues: Map<String, String?>) =
  this.findAttributeFloatValue(attributeName) ?: defaultValues[attributeName]?.toFloat()

private fun UAnnotation.getStringAttribute(attributeName: String, defaultValues: Map<String, String?>) =
  this.findAttributeValue(attributeName)?.evaluateString()?.nullize() ?: defaultValues[attributeName]