/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.wear.preview

import com.android.tools.preview.AnnotatedMethod
import com.android.tools.preview.AnnotationAttributesProvider
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.config.PARAMETER_DEVICE
import com.android.tools.preview.config.PARAMETER_FONT_SCALE
import com.android.tools.preview.config.PARAMETER_GROUP
import com.android.tools.preview.config.PARAMETER_LOCALE
import com.android.tools.preview.config.PARAMETER_NAME

/**
 * Converts the given preview annotation represented by the [attributesProvider] to a
 * [WearTilePreviewElement].
 */
fun <T : Any> previewAnnotationToWearTilePreviewElement(
  attributesProvider: AnnotationAttributesProvider,
  annotatedMethod: AnnotatedMethod<T>,
  previewElementDefinition: T?,
  buildPreviewName: (nameParameter: String?) -> String,
  buildParameterName: (nameParameter: String?) -> String? = { it },
): WearTilePreviewElement<T> {
  val name = attributesProvider.getDeclaredAttributeValue<String?>(PARAMETER_NAME)
  val group = attributesProvider.getDeclaredAttributeValue<String?>(PARAMETER_GROUP)

  val methodName = annotatedMethod.name
  val displaySettings =
    PreviewDisplaySettings(
      name = buildPreviewName(name),
      baseName = methodName,
      parameterName = buildParameterName(name),
      group = group,
      showDecoration = false,
      showBackground = true,
      // will default to black in the view adapter
      backgroundColor = null,
    )

  val configuration =
    PreviewConfiguration.cleanAndGet(
      device = attributesProvider.getStringAttribute(PARAMETER_DEVICE),
      locale = attributesProvider.getStringAttribute(PARAMETER_LOCALE),
      fontScale = attributesProvider.getFloatAttribute(PARAMETER_FONT_SCALE),
    )

  return WearTilePreviewElement(
    displaySettings = displaySettings,
    previewElementDefinition = previewElementDefinition,
    previewBody = annotatedMethod.methodBody,
    methodFqn = annotatedMethod.qualifiedName,
    configuration = configuration,
  )
}
