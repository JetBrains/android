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

package com.android.tools.compose

import com.android.tools.compose.code.state.COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_KEY
import com.android.tools.compose.code.state.COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import icons.StudioIcons
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlighter
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors

private val COMPOSABLE_CALL_DESCRIPTOR =
  AttributesDescriptor(
    ComposeBundle.message("composable.function.rendering.text.attributes.description"),
    COMPOSABLE_CALL_TEXT_ATTRIBUTES_KEY,
  )

private val STATE_READ_DESCRIPTOR =
  AttributesDescriptor(
    ComposeBundle.message("state.read.text.attributes.description"),
    COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY,
  )

private val STATE_READ_SCOPE_DESCRIPTOR =
  AttributesDescriptor(
    ComposeBundle.message("state.read.scope.highlighting.text.attributes.description"),
    COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_KEY,
  )

private val TAG_TO_DESCRIPTOR =
  mapOf(
    "CC" to COMPOSABLE_CALL_TEXT_ATTRIBUTES_KEY,
    "CSR" to COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY,
    "CSRS" to COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_KEY,
    "A" to KotlinHighlightingColors.ANNOTATION,
    "K" to KotlinHighlightingColors.KEYWORD,
    "FD" to KotlinHighlightingColors.FUNCTION_DECLARATION,
    "FP" to KotlinHighlightingColors.PARAMETER,
  )

private val DEMO_TEXT =
  """
  <A>@Composable</A>
  <K>fun</K> <FD>Text</FD>(<FP>text</FP>: <FP>String</FP>) {}

  <A>@Composable</A>
  <K>fun</K> <FD>Greeting</FD>() {
    <CC>Text</CC>(<FP>"Hello"</FP>)
  }
  """
    .trimIndent()

private val STATE_READ_DEMO_TEXT =
  """
  <CSRS><A>@Composable</A>
  <K>fun</K> <FD>ReadsState</FD>(<FP>textState</FP>: <FP>State<String></FP>) {
    <CC>Text</CC>(<FP>textState.<CSR>value</CSR></FP>)
  }</CSRS>
  """
    .trimIndent()

/** A settings page where users can change the style of Compose attributes. */
class ComposeColorSettingsPage : ColorSettingsPage {
  override fun getHighlighter() = KotlinHighlighter()

  override fun getAdditionalHighlightingTagToDescriptorMap() = TAG_TO_DESCRIPTOR

  override fun getIcon() = StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION

  override fun getAttributeDescriptors() =
    buildList {
        add(COMPOSABLE_CALL_DESCRIPTOR)
        if (StudioFlags.COMPOSE_STATE_READ_INLAY_HINTS_ENABLED.get()) {
          add(STATE_READ_DESCRIPTOR)
          add(STATE_READ_SCOPE_DESCRIPTOR)
        }
      }
      .toTypedArray<AttributesDescriptor>()

  override fun getColorDescriptors(): Array<ColorDescriptor> = emptyArray()

  override fun getDisplayName() = ComposeBundle.message("compose")

  override fun getDemoText() = buildString {
    append(DEMO_TEXT)
    if (StudioFlags.COMPOSE_STATE_READ_INLAY_HINTS_ENABLED.get()) {
      append("\n\n").append(STATE_READ_DEMO_TEXT)
    }
  }
}
