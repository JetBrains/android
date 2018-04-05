/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.structure.configurables.ui.TextRenderer
import com.android.tools.idea.gradle.structure.model.meta.DslMode
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.getText
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.STYLE_WAVED
import com.intellij.ui.SimpleTextAttributes.merge

/**
 * A sequence of actions to render a represented value onto a [TextRenderer].
 */
interface ValueRenderer {
  fun renderTo(textRenderer: TextRenderer)
}

private val variableNameAttributes = merge(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, SimpleTextAttributes(0, JBColor.blue))
private val regularAttributes = merge(SimpleTextAttributes.REGULAR_ATTRIBUTES, SimpleTextAttributes(0, JBColor.black))
private val commentAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
private val defaultAttributes = SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
private val errorAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES
private val codeAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES.derive(STYLE_WAVED, null, null, null)

/**
 * Renders the receiver to the [textRenderer] with any known values handled by renderers from [knownValues].
 */
fun <PropertyT : Any> ParsedValue<PropertyT>.renderTo(textRenderer: TextRenderer, knownValues: Map<PropertyT?, ValueRenderer>) =
  let { value ->
    val knownRenderer = when {
      value === ParsedValue.NotSet -> knownValues[null]
      value is ParsedValue.Set.Parsed && (value.dslText?.mode ?: DslMode.LITERAL) == DslMode.LITERAL -> knownValues[value.value]
      else -> null
    }
    when {
      knownRenderer != null -> knownRenderer.renderTo(textRenderer)
      value is ParsedValue.Set.Parsed && value.dslText?.mode == DslMode.REFERENCE -> {
        textRenderer.append(value.getText(), variableNameAttributes)
        if (value.value != null) {
          textRenderer.append(" = ", regularAttributes)
          val valueDescription = knownValues[value.value]
          if (valueDescription != null) {
            valueDescription.renderTo(textRenderer)
          }
          else {
            textRenderer.append(value.value.toString(), regularAttributes)
          }
        }
      }
      value is ParsedValue.Set.Parsed && value.dslText?.mode == DslMode.INTERPOLATED_STRING -> {
        textRenderer.append(value.getText(), variableNameAttributes)
        if (value.value != null) {
          textRenderer.append(" = \"${value.value}\"", regularAttributes)
        }
      }
      value is ParsedValue.Set.Parsed && value.dslText?.mode == DslMode.OTHER_UNPARSED_DSL_TEXT -> {
        textRenderer.append("\$\$", variableNameAttributes)
        textRenderer.append(value.dslText.text.orEmpty(), codeAttributes)
      }
      value is ParsedValue.Set.Invalid -> {
        textRenderer.append("${value.dslText} ", regularAttributes)
        textRenderer.append("(${value.errorMessage.takeUnless { it == "" } ?: "invalid value"})", errorAttributes)
      }
      else -> textRenderer.append(value.getText(), regularAttributes)
    }
  }

/**
 * Builds renderers for known values described by [ValueDescriptor]s.
 */
fun <PropertyT : Any> buildKnownValueRenderers(
  knownValues: List<ValueDescriptor<PropertyT>>?, defaultValue: PropertyT?
): Map<PropertyT?, ValueRenderer> {
  val knownValuesMap = knownValues?.associate { it.value to it.description }.orEmpty()
  val result = mutableListOf<Pair<PropertyT?, ValueRenderer>>()
  if (defaultValue != null) {
    // Note: having this value prevents users from inputting string value "($default)". However, since there are just few properties with
    // default string values and the values in parentheses do not make sense it is safe to recognize this value as NotSet.
    result.add(null to object : ValueRenderer {
      override fun renderTo(textRenderer: TextRenderer) {
        val defaultValueDescription = knownValuesMap[defaultValue]
        textRenderer.append(defaultValue.toString(), defaultAttributes)
        if (defaultValueDescription != null) {
          textRenderer.append(" ($defaultValueDescription)", defaultAttributes)
        }
      }
    })
  }
  if (knownValues != null) {
    result.addAll(knownValues.map {
      it.value to object : ValueRenderer {
        override fun renderTo(textRenderer: TextRenderer) {
          textRenderer.append(it.value?.let { it.toString() + " " } ?: "", regularAttributes)
          textRenderer.append("(${it.description})", commentAttributes)
        }
      }
    })
  }
  return result.associate { it.first to it.second }
}
