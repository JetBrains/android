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
package com.android.tools.preview

import com.android.tools.preview.config.PARAMETER_BACKGROUND_COLOR
import com.android.tools.preview.config.PARAMETER_GROUP
import com.android.tools.preview.config.PARAMETER_NAME
import com.android.tools.preview.config.PARAMETER_SHOW_BACKGROUND
import com.android.tools.preview.config.PARAMETER_SHOW_DECORATION
import com.android.tools.preview.config.PARAMETER_SHOW_SYSTEM_UI

/**
 * Converts the given preview annotation represented by the [attributesProvider] to a
 * [ComposePreviewElement].
 */
fun <T : Any> previewAnnotationToPreviewElement(
  attributesProvider: AnnotationAttributesProvider,
  annotatedMethod: AnnotatedMethod<T>,
  previewElementDefinition: T?,
  parameterizedElementConstructor: (SingleComposePreviewElementInstance<T>, Collection<PreviewParameter>) -> ComposePreviewElement<T>,
  overrideGroupName: String? = null,
  buildPreviewName: (nameParameter: String?) -> String,
  buildParameterName: (nameParameter: String?) -> String? = { it },
): ComposePreviewElement<T> {
  val composableMethod = annotatedMethod.qualifiedName
  val baseName = annotatedMethod.name
  val parameter = attributesProvider.getDeclaredAttributeValue<String?>(PARAMETER_NAME)
  val previewName = buildPreviewName(parameter)
  val parameterName = buildParameterName(parameter)
  val groupName = overrideGroupName ?: attributesProvider.getDeclaredAttributeValue(PARAMETER_GROUP)
  val showDecorations =
    attributesProvider.getBooleanAttribute(PARAMETER_SHOW_DECORATION)
    ?: (attributesProvider.getBooleanAttribute(PARAMETER_SHOW_SYSTEM_UI)) ?: false
  val showBackground = attributesProvider.getBooleanAttribute(PARAMETER_SHOW_BACKGROUND) ?: false
  // We don't use the library's default value for BackgroundColor and instead use a value defined
  // here, see PreviewElement#toPreviewXml.
  val backgroundColor =
    attributesProvider.getDeclaredAttributeValue<Any>(PARAMETER_BACKGROUND_COLOR)
  val backgroundColorString =
    when (backgroundColor) {
      is Int -> backgroundColor.toString(16)
      is Long -> backgroundColor.toString(16)
      is String -> backgroundColor.toLongOrNull()?.toString(16)
      else -> null
    }?.let { "#$it" }

  // If the same composable functions is found multiple times, only keep the first one. This usually
  // will happen during
  // copy & paste and both the compiler and Studio will flag it as an error.
  val displaySettings =
    PreviewDisplaySettings(
      previewName,
      baseName,
      parameterName,
      groupName,
      showDecorations,
      showBackground,
      backgroundColorString
    )

  val parameters = getPreviewParameters(annotatedMethod.parameterAnnotations)
  val basePreviewElement =
    SingleComposePreviewElementInstance(
      composableMethod,
      displaySettings,
      previewElementDefinition,
      annotatedMethod.methodBody,
      attributesToConfiguration(attributesProvider)
    )
  return if (!parameters.isEmpty()) {
    parameterizedElementConstructor(basePreviewElement, parameters)
  } else {
    basePreviewElement
  }
}

/**
 * Returns a list of [PreviewParameter] for the given [Collection] of parameters annotated with
 * `PreviewParameter`, where each parameter is represented by a [Pair] of its name and
 * [AnnotationAttributesProvider] for the annotation.
 */
private fun getPreviewParameters(
  attributesProviders: Collection<Pair<String, AnnotationAttributesProvider>>
): Collection<PreviewParameter> =
  attributesProviders.mapIndexedNotNull { index, (name, attributesProvider) ->
    val providerClassFqn =
      (attributesProvider.findClassNameValue("provider")) ?: return@mapIndexedNotNull null
    val limit = attributesProvider.getIntAttribute("limit") ?: Int.MAX_VALUE
    PreviewParameter(name, index, providerClassFqn, limit)
  }