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
package com.android.tools.idea.compose.annotator.check.device

import com.android.tools.idea.compose.annotator.check.common.CheckRule
import com.android.tools.idea.compose.annotator.check.common.ParameterRule

internal enum class DeviceSpecRule(
  override val requiredParameters: List<ParameterRule>,
  override val optionalParameters: List<ParameterRule>
) : CheckRule {
  Legacy(
    requiredParameters =
      listOf(
        LegacyParameterRule.shape,
        LegacyParameterRule.width,
        LegacyParameterRule.height,
        LegacyParameterRule.unit,
        LegacyParameterRule.dpi
      ),
    optionalParameters = listOf(LegacyParameterRule.id)
  ),
  LanguageBased(
    requiredParameters = listOf(LanguageParameterRule.width, LanguageParameterRule.height),
    optionalParameters =
      listOf(
        LanguageParameterRule.round,
        LanguageParameterRule.orientation,
        LanguageParameterRule.chinSize,
        LanguageParameterRule.dpi
      )
  ),
  LanguageWithParentId(
    requiredParameters = listOf(LanguageParameterRule.parent),
    optionalParameters = listOf(LanguageParameterRule.orientation)
  )
}
