/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import kotlin.test.assertEquals
import org.junit.Test

class ParametrizedComposePreviewElementInstanceTest {

  @Test
  fun testPreviewWithoutCustomDisplayName() {
    val basePreviewElementName = "PreviewComposableName"
    val parameterName = "user"
    val numInstances = 4
    val previews =
      (0..numInstances).map { index ->
        ParametrizedComposePreviewElementInstance(
          basePreviewElement = previewInstance(name = basePreviewElementName),
          parameterName = parameterName,
          providerClassFqn = "ProviderClass",
          index = index,
          maxIndex = numInstances,
          displayName = null,
        )
      }

    previews.forEach {
      assertEquals("$basePreviewElementName ($parameterName ${it.index})", it.displaySettings.name)
    }
  }

  @Test
  fun testPreviewWithCustomDisplayName() {
    val basePreviewElementName = "PreviewComposableName"
    val parameterName = "user"
    val parameterDisplayNames =
      arrayOf(
        "A" to "$basePreviewElementName (A)",
        "B" to "$basePreviewElementName (B)",
        "" to "$basePreviewElementName ($parameterName 2)",
        null to "$basePreviewElementName ($parameterName 3)",
      )
    val previews =
      parameterDisplayNames.mapIndexed { index, (displayName, _) ->
        ParametrizedComposePreviewElementInstance(
          basePreviewElement = previewInstance(name = basePreviewElementName),
          parameterName = parameterName,
          providerClassFqn = "ProviderClass",
          index = index,
          maxIndex = parameterDisplayNames.size,
          displayName = displayName,
        )
      }

    previews.forEach {
      val expectedDisplayName = parameterDisplayNames[it.index].second
      assertEquals(expectedDisplayName, it.displaySettings.name)
    }
  }

  @Test
  fun testPreviewParameterNameWithCustomDisplayName() {
    val basePreviewElementName = "PreviewComposableName"
    val parameterName = "user"
    val parameterDisplayNames =
      arrayOf(
        "A" to "$basePreviewElementName - A",
        "B" to "$basePreviewElementName - B",
        "" to "$basePreviewElementName - $parameterName 2",
        null to "$basePreviewElementName - $parameterName 3",
      )
    val previews =
      parameterDisplayNames.mapIndexed { index, (displayName, _) ->
        ParametrizedComposePreviewElementInstance(
          basePreviewElement = previewInstance(name = basePreviewElementName),
          parameterName = parameterName,
          providerClassFqn = "ProviderClass",
          index = index,
          maxIndex = parameterDisplayNames.size,
          displayName = displayName,
        )
      }

    previews.forEach {
      val expectedDisplayName = parameterDisplayNames[it.index].second
      assertEquals(expectedDisplayName, it.displaySettings.parameterName)
    }
  }

  private fun previewInstance(name: String, group: String? = null) =
    SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
      methodFqn = "ComposableName",
      displaySettings =
        PreviewDisplaySettings(
          name = name,
          baseName = "ComposableName",
          parameterName = name,
          group = group,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
          organizationGroup = "",
          organizationName = "",
        ),
      previewElementDefinition = null,
      previewBody = null,
      configuration = PreviewConfiguration.cleanAndGet(),
    )
}
