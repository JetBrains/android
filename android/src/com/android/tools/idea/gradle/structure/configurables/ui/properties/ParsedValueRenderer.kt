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
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.STYLE_WAVED
import com.intellij.ui.SimpleTextAttributes.merge

/**
 * A sequence of actions to render a represented value onto a [TextRenderer].
 */
interface ValueRenderer {
  fun renderTo(textRenderer: TextRenderer): Boolean
}

private val variableNameAttributes = merge(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, SimpleTextAttributes(0, JBColor.blue))
private val regularAttributes = merge(SimpleTextAttributes.REGULAR_ATTRIBUTES, SimpleTextAttributes(0, JBColor.black))
private val commentAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
private val defaultAttributes = SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
private val errorAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES
private val codeAttributes = merge(SimpleTextAttributes.REGULAR_ATTRIBUTES, SimpleTextAttributes(0, JBColor.black))

/**
 * Renders the receiver (which may be of [List], [Map] or any simple type to the [textRenderer] with any known values handled by renderers
 * from [knownValues]. Returns true in the case of non-empty output.
 */
fun ParsedValue<Any>.renderAnyTo(
  textRenderer: TextRenderer,
  knownValues: Map<ParsedValue<Any>, ValueRenderer>
): Boolean {
  fun renderAny(value: Any) =
    when (value) {
      is ParsedValue<*> -> value.renderTo(textRenderer, { toString() }, knownValues)
      else -> {
        val text = toString()
        if (text.isNotEmpty()) {
          textRenderer.append(text, regularAttributes)
        }
        text.isNotEmpty()
      }
    }

  val literalValue = maybeLiteralValue
  return when (literalValue) {
    is Map<*, *> -> {
      textRenderer.append("[", regularAttributes)
      literalValue.entries.forEachIndexed { index, entry ->
        if (index > 0) textRenderer.append(", ", regularAttributes)
        textRenderer.append(entry.key.toString(), regularAttributes)
        textRenderer.append(" : ", regularAttributes)
        renderAny(entry.value ?: "")
      }
      if (literalValue.isEmpty()) {
        textRenderer.append(":", regularAttributes)
      }
      textRenderer.append("]", regularAttributes)
      true
    }
    is List<*> -> {
      textRenderer.append("[", regularAttributes)
      literalValue.forEachIndexed { index, v->
        if (index > 0) textRenderer.append(", ", regularAttributes)
        renderAny(v ?: "")
      }
      textRenderer.append("]", regularAttributes)
      true
    }
    else -> renderTo(textRenderer, { toString() }, knownValues)
  }
}

/**
 * Renders the receiver to the [textRenderer] with any known values handled by renderers from [knownValues]. Returns true in the case of
 * non-empty output.
 */
fun <PropertyT : Any> ParsedValue<PropertyT>.renderTo(
  textRenderer: TextRenderer,
  formatValue: PropertyT.() -> String,
  knownValues: Map<ParsedValue<PropertyT>, ValueRenderer>
): Boolean =
  let { value ->
    val knownRenderer = knownValues[value]
    when {
      knownRenderer != null -> knownRenderer.renderTo(textRenderer)
      value is ParsedValue.Set.Parsed && value.dslText is DslText.Reference -> {
        textRenderer.append(value.getText(formatValue), variableNameAttributes)
        if (value.value != null) {
          val valueDescription = knownValues[ParsedValue.Set.Parsed(value.value, DslText.Literal)]
          if (valueDescription != null) {
            textRenderer.append(" : ", commentAttributes)
            valueDescription.renderTo(makeCommentRenderer(textRenderer))
          }
          else {
            val formattedValue = value.value.formatValue()
            if (!formattedValue.isEmpty()) {
              textRenderer.append(" : ", commentAttributes)
              textRenderer.append(formattedValue, commentAttributes)
            }
          }
        }
        true
      }
      value is ParsedValue.Set.Parsed && value.dslText is DslText.InterpolatedString -> {
        textRenderer.append(value.getText(formatValue), variableNameAttributes)
        if (value.value != null) {
          textRenderer.append(" : \"${value.value.formatValue()}\"", commentAttributes)
        }
        true
      }
      value is ParsedValue.Set.Parsed && value.dslText is DslText.OtherUnparsedDslText -> {
        textRenderer.append("\$", variableNameAttributes)
        textRenderer.append(value.dslText.text, codeAttributes)
        true
      }
      else -> {
        val formattedText = value.getText(formatValue)
        textRenderer.append(formattedText, regularAttributes)
        formattedText.isNotEmpty()
      }
    }
  }

fun <PropertyT : Any> Annotated<ParsedValue<PropertyT>>.renderTo(
  textRenderer: TextRenderer,
  formatValue: PropertyT.() -> String,
  knownValues: Map<ParsedValue<PropertyT>, ValueRenderer>
): Boolean =
  when (annotation) {
    is ValueAnnotation.ErrorOrWarning -> {
      val result = value.renderTo(makeUnparsedRenderer(textRenderer), formatValue, knownValues)
      if (result) {
        textRenderer.append(" ", errorAttributes)
      }
      textRenderer.append("(${annotation.message})", errorAttributes)
      result
    }
    else -> value.renderTo(textRenderer, formatValue, knownValues)
  }


/**
 * Builds renderers for known values described by [ValueDescriptor]s.
 */
fun <PropertyT : Any> buildKnownValueRenderers(
  knownValues: KnownValues<PropertyT>, formatValue: PropertyT.() -> String, defaultValue: PropertyT?
): Map<ParsedValue<PropertyT>, ValueRenderer> {
  val knownValuesMap = knownValues.literals.associate { it.value to it.description }
  val result = mutableListOf<Pair<ParsedValue<PropertyT>, ValueRenderer>>()
  if (defaultValue != null) {
    result.add(ParsedValue.NotSet to object : ValueRenderer {
      override fun renderTo(textRenderer: TextRenderer): Boolean {
        val defaultValueDescription = knownValuesMap[ParsedValue.Set.Parsed(defaultValue, DslText.Literal)]
        val formattedValue = defaultValue.formatValue()
        textRenderer.append(formattedValue, defaultAttributes)
        if (defaultValueDescription != null) {
          if (!formattedValue.isEmpty()) {
            textRenderer.append(" ", defaultAttributes)
          }
          textRenderer.append("($defaultValueDescription)", defaultAttributes)
        }
        return formattedValue.isNotEmpty() || defaultValueDescription != null
      }
    })
  }
  result.addAll(knownValues.literals.map {
    it.value to object : ValueRenderer {
      override fun renderTo(textRenderer: TextRenderer): Boolean {
        val notEmptyValue = if (it.value !== ParsedValue.NotSet) {
          val notEmptyValue = it.value.annotated().renderTo(textRenderer, formatValue, mapOf())
          if (notEmptyValue && it.description != null) {
            textRenderer.append(" ", regularAttributes)
          }
          notEmptyValue
        }
        else {
          false
        }
        if (it.description != null) {
          textRenderer.append("(${it.description})", commentAttributes)
        }
        return notEmptyValue || it.description != null
      }
    }
  })
  return result.associate { it.first to it.second }
}

fun makeCommentRenderer(textRenderer: TextRenderer) = object : TextRenderer {
  // Replace 'regular' text color with 'comment' text color.
  override fun append(text: String, attributes: SimpleTextAttributes) =
    textRenderer.append(
      text,
      if (attributes.fgColor == regularAttributes.fgColor) attributes.derive(0, commentAttributes.fgColor, null, null) else attributes
    )
}

fun makeUnparsedRenderer(textRenderer: TextRenderer) = object : TextRenderer {
  // Add 'waved' text attribute.
  override fun append(text: String, attributes: SimpleTextAttributes) =
    textRenderer.append(
      text,
      attributes.derive(attributes.style or STYLE_WAVED, null, null, null)
    )
}
