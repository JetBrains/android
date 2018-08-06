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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property2.api.ActionIconButton
import com.android.tools.idea.common.property2.api.FlagsPropertyItem
import com.android.tools.idea.common.property2.api.NewPropertyItem
import com.android.tools.idea.common.property2.api.PropertiesTable

/**
 * A [NelePropertyItem] where it is possible to edit the name of the property.
 *
 * The property is initially created with an empty name and an unknown type.
 * When a name is specified, it is matched against all known attributes. If
 * found this property item will act as a delegate to the matched property.
 */
class NeleNewPropertyItem(model: NelePropertiesModel,
                          var properties: PropertiesTable<NelePropertyItem>)
  : NelePropertyItem("", "", NelePropertyType.UNKNOWN, null, "", model, listOf()), NewPropertyItem, FlagsPropertyItem<NeleFlagPropertyItem> {

  override var namespace: String = ""
    private set

  override var name: String = ""
    set(value) {
      val (propertyNamespace, propertyName) = parseName(value)
      namespace = propertyNamespace
      field = propertyName
      delegate = findDelegate(propertyNamespace, propertyName)

      // Give the model a change to hide expanded flag items
      model.firePropertyValueChange()
    }

  // There should only be one instance of NeleNewPropertyItem per Property panel.
  override fun equals(other: Any?) = other is NeleNewPropertyItem
  // The hashCode can be an arbitrary number since we only have 1 instance
  override fun hashCode() = 517

  /**
   * When the property name is set to something valid, the [delegate] will be not null.
   * All remaining properties and functions should delegate to this [delegate] if present.
   */
  var delegate: NelePropertyItem? = null
    private set

  override val nameEditingSupport = object : EditingSupport {
    override val completion = { getPropertyNamesWithPrefix() }
    override val validation = { text: String -> validate(text)}
  }

  override var value: String?
    get() = delegate?.value
    set(value) {
      delegate?.value = value
    }

  override val resolvedValue: String?
    get() = delegate?.resolvedValue

  override val rawValue: String?
    get() = delegate?.rawValue

  override val isReference: Boolean
    get() = delegate?.isReference == true

  override val tooltipForName: String
    get() = delegate?.tooltipForName ?: ""

  override val tooltipForValue: String
    get() = delegate?.tooltipForValue ?: ""

  override val browseButton: ActionIconButton?
    get() = delegate?.browseButton

  override val children: List<NeleFlagPropertyItem>
    get() = (delegate as? NeleFlagsPropertyItem)?.children ?: emptyList()

  override fun flag(itemName: String): NeleFlagPropertyItem? {
    return (delegate as? NeleFlagsPropertyItem)?.flag(itemName)
  }

  override val maskValue: Int
    get() = (delegate as? NeleFlagsPropertyItem)?.maskValue ?: 0

  override val firstComponent: NlComponent?
    get() = properties.first?.components?.firstOrNull()

  private fun parseName(value: String): Pair<String, String> {
    val prefixIndex = value.indexOf(":")
    if (prefixIndex < 0) {
      return Pair("", value)
    }
    val prefix = value.substring(0, prefixIndex)
    val name = value.substring(prefixIndex + 1)
    val namespace = namespaceResolver.prefixToUri(prefix) ?: ANDROID_URI
    return Pair(namespace, name)
  }

  private fun findDelegate(propertyNamespace: String, propertyName: String): NelePropertyItem? {
    var property = properties.getOrNull(propertyNamespace, propertyName)
    if (property != null) {
      return property
    }
    if (propertyNamespace == TOOLS_URI) {
      for (ns in properties.namespaces) {
        property = properties.getOrNull(ns, propertyName)
        if (property != null) {
          return property.designProperty
        }
      }
    }
    return null
  }

  private fun getPropertyNamesWithPrefix(): List<String> {
    val resolver = namespaceResolver
    return properties.values.filter { it.rawValue == null }.map { getPropertyNameWithPrefix(it, resolver) }
  }

  private fun getPropertyNameWithPrefix(property: NelePropertyItem, resolver: ResourceNamespace.Resolver): String {
    val name = property.name
    val prefix = resolver.uriToPrefix(property.namespace)
    return if (prefix.isNullOrEmpty()) name else "$prefix:$name"
  }

  private fun validate(text: String): Pair<EditingErrorCategory, String> {
    val (propertyNamespace, propertyName) = parseName(text)
    val property = findDelegate(propertyNamespace, propertyName)
    return when {
      property == null -> Pair(EditingErrorCategory.ERROR, "No property found by the name: $text")
      property.rawValue != null -> Pair(EditingErrorCategory.ERROR, "A property by the name: $text is already specified")
      else -> EDITOR_NO_ERROR
    }
  }
}
