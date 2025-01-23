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

import com.android.SdkConstants.ATTR_RESOURCE
import com.android.SdkConstants.TAG_COMPLICATION
import com.android.SdkConstants.TAG_IMAGE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wear.dwf.dom.raw.ComplicationDataSourceCompletionProvider.Companion.COMPLICATION_DATA_SOURCES
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.XmlAttributeValuePattern
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile

/**
 * A [CompletionContributor] that adds complication data sources when completing the `resource`
 * attribute for `<Image>` tags that are under a `<Complication>` tag.
 *
 * @see COMPLICATION_DATA_SOURCES
 * @see <a href="https://developer.android.com/reference/wear-os/wff/group/part/image/image">WFF
 *   Image</a>
 */
class RawWatchFaceComplicationDataSourceCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement().inside(XmlPatterns.xmlAttributeValue()),
      ComplicationDataSourceCompletionProvider(),
    )
  }
}

private class ComplicationDataSourceCompletionProvider :
  CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet,
  ) {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get()) return
    val element = parameters.position.parent as? XmlAttributeValue ?: return
    val xmlFile = element.containingFile as? XmlFile ?: return
    if (!isDeclarativeWatchFaceFile(xmlFile)) return
    if (XmlAttributeValuePattern.getLocalName(element) != ATTR_RESOURCE) return
    if (element.parentOfType<XmlTag>()?.name != TAG_IMAGE) return

    val isChildOfComplication =
      element.parents(false).any { it is XmlTag && it.name == TAG_COMPLICATION }
    if (!isChildOfComplication) return

    for (complicationDataSource in COMPLICATION_DATA_SOURCES) {
      result.addElement(
        LookupElementBuilder.create(complicationDataSource)
          .withPresentableText("[$complicationDataSource]")
          .withInsertHandler(surroundWithBracketsIfNeeded(complicationDataSource))
      )
    }
  }

  companion object {
    val COMPLICATION_DATA_SOURCES =
      arrayOf(
        "COMPLICATION.MONOCHROMATIC_IMAGE",
        "COMPLICATION.SMALL_IMAGE",
        "COMPLICATION.PHOTO_IMAGE",
      )
  }
}

/**
 * There seems to be an issue with autocomplete and the `[` character. If the LookupElement text
 * starts with `[`, it's not possible to autocomplete if the text has some characters.
 *
 * Instead, we use a lookup text without the `[`, `]` characters. This function creates an
 * [InsertHandler] to insert those characters, if needed, after the auto-complete happens.
 */
private fun surroundWithBracketsIfNeeded(textToSurroundWithBrackets: String) =
  InsertHandler<LookupElement> { context, lookupElement ->
    val textWithSurroundingCharacters =
      context.document.getText(TextRange(context.startOffset - 1, context.tailOffset + 1))
    val textWithBrackets = StringBuilder()
    if (!textWithSurroundingCharacters.startsWith("[")) {
      textWithBrackets.append("[")
    }
    textWithBrackets.append(textToSurroundWithBrackets)
    if (!textWithSurroundingCharacters.endsWith("]")) {
      textWithBrackets.append("]")
    }
    context.document.replaceString(
      context.startOffset,
      context.tailOffset,
      textWithBrackets.toString(),
    )
  }
