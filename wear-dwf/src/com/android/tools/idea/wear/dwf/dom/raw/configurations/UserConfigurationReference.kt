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
package com.android.tools.idea.wear.dwf.dom.raw.configurations

import com.android.tools.idea.wear.dwf.WFFConstants.CONFIGURATION_PREFIX
import com.android.tools.idea.wear.dwf.dom.raw.extractUserConfigurations
import com.android.tools.idea.wear.dwf.dom.raw.insertBracketsAroundIfNeeded
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.xml.XmlFile

/**
 * Reference towards a Declarative Watch Face user configuration. User configurations are defined
 * under the `<UserConfigurations>` tag in the given Declarative [watchFaceFile].
 *
 * The given [referenceValue] must be in the form `[CONFIGURATION.<configurationId>]` or, in the
 * case of color configurations, `[CONFIGURATION.<configurationId>.<colorIndex>]`.
 *
 * Color configurations can also refer to a color index. The color index corresponds to an index of
 * a `<ColorOption>`'s `colors` attribute array. For example, in the following the number can be
 * `0`, `1` or `2`:
 * ```xml
 *    <ColorOption
 *        colors="#ff577c3e #ff577c3e #ff577c3e"
 *        displayName="ith_option_display_name"
 *        id="0"
 *        screenReaderText="ith_option_display_name" />
 * ```
 *
 * If the `<ColorOption>`'s `colors` attribute only has one value, then it's acceptable to not
 * specify a color index.
 *
 * However, as the different options depends on whatever a Watch Face User selects on their watch,
 * the references will point to the parent `<ColorConfiguration>`.
 *
 * The [filter] parameter is used to filter user configurations that can be resolved by the
 * reference and that should appear in the variants.
 *
 * @see <a
 *   href="https://developer.android.com/training/wearables/wff/personalization/user-configurations">WFF
 *   User configurations</a>
 */
class UserConfigurationReference(
  element: PsiElement,
  private val watchFaceFile: XmlFile,
  private val referenceValue: String = "",
  private val filter: (UserConfiguration) -> Boolean = { true },
) : PsiReferenceBase<PsiElement>(element) {

  private val userConfigurationIdParts = extractUserConfigurationIdParts()
  val userConfigurationId = userConfigurationIdParts?.firstOrNull()
  val colorIndex = if (userConfigurationIdParts?.size == 2) userConfigurationIdParts[1] else null

  override fun resolve(): PsiElement? {
    if (userConfigurationIdParts == null) return null
    if (userConfigurationId == null) return null

    // there can only be maximum 2 parts, one for the id and one for a color index
    if (userConfigurationIdParts.size > 2) return null

    val resolvedConfiguration =
      watchFaceFile
        .extractUserConfigurations()
        .filter { filter(it) }
        .find { it.id == userConfigurationId } ?: return null

    val hasColorIndex = colorIndex != null
    if (hasColorIndex && resolvedConfiguration !is ColorConfiguration) return null
    return resolvedConfiguration.xmlTag
  }

  override fun getVariants(): Array<Any> {
    val ids =
      watchFaceFile
        .extractUserConfigurations()
        .filter { filter(it) }
        .flatMap { userConfiguration ->
          if (
            userConfiguration is ColorConfiguration && userConfiguration.colorIndices.count() > 1
          ) {
            userConfiguration.colorIndices.map { colorIndex ->
              "${userConfiguration.id}.$colorIndex"
            }
          } else {
            listOf(userConfiguration.id)
          }
        }
    return ids
      .map { "[$CONFIGURATION_PREFIX$it]" }
      .map { LookupElementBuilder.create(it).insertBracketsAroundIfNeeded() }
      .toTypedArray()
  }

  private fun extractUserConfigurationIdParts(): List<String>? {
    if (!referenceValue.startsWith("[$CONFIGURATION_PREFIX")) return null
    if (!referenceValue.endsWith("]")) return null
    return referenceValue
      .removeSurrounding(prefix = "[$CONFIGURATION_PREFIX", suffix = "]")
      .split(".")
  }
}
