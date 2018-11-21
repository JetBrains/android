/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import org.jetbrains.android.dom.attrs.AttributeDefinition
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.resources.ResourceType
import org.jetbrains.android.dom.AndroidDomUtil

/**
 * Temporary type resolver.
 *
 * Eventually we want the library and framework to specify the correct type for each attribute.
 * This is the fallback if this information is not available.
 */
object TypeResolver {

  fun resolveType(name: String, attribute: AttributeDefinition?): NelePropertyType {
    return lookupByName(name)
           ?: bySpecialType(name)
           ?: fromAttributeDefinition(attribute)
           ?: fallbackByName(name)
  }

  private fun bySpecialType(name: String): NelePropertyType? {
    val types = AndroidDomUtil.getSpecialResourceTypes(name)
    for (type in types) {
      when (type) {
        ResourceType.ID -> return NelePropertyType.ID
        //TODO: expand in a followup CL
        else -> {}
      }
    }
    return null
  }

  private fun fromAttributeDefinition(attribute: AttributeDefinition?): NelePropertyType? {
    if (attribute == null) return null
    var subType: NelePropertyType? = null

    for (format in attribute.formats) {
      when (format) {
        AttributeFormat.BOOLEAN -> return NelePropertyType.THREE_STATE_BOOLEAN
        AttributeFormat.COLOR -> return NelePropertyType.COLOR
        AttributeFormat.DIMENSION -> return NelePropertyType.DIMENSION
        AttributeFormat.FLOAT -> return NelePropertyType.FLOAT
        AttributeFormat.FRACTION -> return NelePropertyType.FRACTION
        AttributeFormat.INTEGER -> return NelePropertyType.INTEGER
        AttributeFormat.STRING -> return NelePropertyType.STRING
        AttributeFormat.FLAGS -> return NelePropertyType.FLAGS
        AttributeFormat.ENUM -> subType = NelePropertyType.ENUM
        else -> {}
      }
    }
    return subType
  }

  private fun lookupByName(name: String) =
    when (name) {
      SdkConstants.ATTR_STYLE -> NelePropertyType.STYLE
      SdkConstants.ATTR_CLASS -> NelePropertyType.FRAGMENT
      SdkConstants.ATTR_LAYOUT,
      SdkConstants.ATTR_SHOW_IN -> NelePropertyType.LAYOUT
      SdkConstants.ATTR_FONT_FAMILY -> NelePropertyType.FONT
      else -> null
    }

  private fun fallbackByName(name: String): NelePropertyType {
    val parts = split(name)
    if ("drawable" in parts) {
      return NelePropertyType.DRAWABLE
    }
    if ("color" in parts) {
      return NelePropertyType.COLOR
    }
    if ("text" in parts && "appearance" in parts) {
      return NelePropertyType.TEXT_APPEARANCE
    }
    if ("style" in parts) {
      return NelePropertyType.STYLE
    }
    return NelePropertyType.STRING
  }

  private fun split(name: String): Set<String> {
    val parts = mutableSetOf<String>()
    var part = name
    while (!part.isEmpty()) {
      val index = part.indexOfFirst { it.isUpperCase() }
      if (index > 0) {
        parts.add(part.substring(0, index))
      }
      else if (index < 0) {
        parts.add(part)
      }
      part = if (index >= 0) part[index].toLowerCase().toString() + part.substring(index + 1) else ""
    }
    return parts
  }
}
