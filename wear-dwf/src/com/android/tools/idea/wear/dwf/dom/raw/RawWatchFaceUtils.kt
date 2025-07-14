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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.SdkConstants.TAG_WATCH_FACE
import com.android.tools.idea.wear.dwf.WFFConstants.ATTRIBUTE_COLORS
import com.android.tools.idea.wear.dwf.WFFConstants.ATTRIBUTE_ID
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_BOOLEAN_CONFIGURATION
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_COLOR_CONFIGURATION
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_COLOR_OPTION
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_LIST_CONFIGURATION
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_PHOTOS_CONFIGURATION
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_USER_CONFIGURATIONS
import com.android.tools.idea.wear.dwf.dom.raw.configurations.BooleanConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.ColorConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.ListConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.PhotosConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.UnknownConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.UserConfiguration
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.xml.XmlFile
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Adds an [InsertHandler] that inserts the `[` and `]` around
 * [LookupElementBuilder.getLookupString] after the auto-complete happens, if needed.
 *
 * This helps prevent having the brackets appear in double.
 */
fun LookupElementBuilder.insertBracketsAroundIfNeeded() =
  withInsertHandler { context, lookupElement ->
    val textWithoutBrackets = lookupElement.lookupString.removeSurrounding("[", "]")
    val textWithSurroundingCharacters =
      context.document.getText(TextRange(context.startOffset - 1, context.tailOffset + 1))
    val textWithBrackets = StringBuilder()
    if (!textWithSurroundingCharacters.startsWith("[")) {
      textWithBrackets.append("[")
    }
    textWithBrackets.append(textWithoutBrackets)
    if (!textWithSurroundingCharacters.endsWith("]")) {
      textWithBrackets.append("]")
    }
    context.document.replaceString(
      context.startOffset,
      context.tailOffset,
      textWithBrackets.toString(),
    )
  }

/** Extracts [UserConfiguration]s from a Declarative Watch Face file. */
@RequiresReadLock
fun XmlFile.extractUserConfigurations(): List<UserConfiguration> {
  if (rootTag?.name != TAG_WATCH_FACE) return emptyList()
  val userConfigurationTags =
    rootTag?.findSubTags(TAG_USER_CONFIGURATIONS)?.flatMap { it.subTags.toList() }
      ?: return emptyList()

  return userConfigurationTags.mapNotNull { userConfigurationTag ->
    val id = userConfigurationTag.getAttribute(ATTRIBUTE_ID)?.value ?: return@mapNotNull null

    when (userConfigurationTag.name) {
      TAG_COLOR_CONFIGURATION -> {
        val availableColorIndices =
          userConfigurationTag.subTags
            .filter { it.name == TAG_COLOR_OPTION }
            .firstNotNullOfOrNull { colorOptionTag ->
              colorOptionTag.getAttribute(ATTRIBUTE_COLORS)?.value?.split("\\s+".toRegex())
            }
            ?.indices ?: IntRange.EMPTY
        ColorConfiguration(id, userConfigurationTag, availableColorIndices)
      }
      TAG_LIST_CONFIGURATION -> ListConfiguration(id, userConfigurationTag)
      TAG_BOOLEAN_CONFIGURATION -> BooleanConfiguration(id, userConfigurationTag)
      TAG_PHOTOS_CONFIGURATION -> PhotosConfiguration(id, userConfigurationTag)
      else -> UnknownConfiguration(id, userConfigurationTag)
    }
  }
}
