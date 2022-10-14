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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.TOOLS_PREFIX
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditorCompletion
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.property.panel.api.ActionIconButton
import com.android.tools.property.panel.api.FlagsPropertyGroupItem
import com.android.tools.property.panel.api.FlagsPropertyItem
import com.android.tools.property.panel.api.NewPropertyItem
import com.android.tools.property.panel.api.PropertiesTable
import org.jetbrains.android.dom.attrs.AttributeDefinition

/**
 * A [NlPropertyItem] where it is possible to edit the name of the property.
 *
 * The property is initially created with an empty name and an unknown type.
 * When a name is specified, it is matched against all known attributes. If
 * found this property item will act as a delegate to the matched property.
 */
class NlNewPropertyItem(model: NlPropertiesModel,
                        var properties: PropertiesTable<NlPropertyItem>,
                        val filter: (NlPropertyItem) -> Boolean = { true },
                        val delegateUpdated: (NlNewPropertyItem) -> Unit = {})
  : NlPropertyItem("", "", NlPropertyType.UNKNOWN, null, "", "", model, listOf()), NewPropertyItem,
    FlagsPropertyGroupItem<NlFlagPropertyItem> {

  override var namespace: String = ""
    get() = delegate?.namespace ?: field
    private set

  override var name: String = ""
    get() = delegate?.name ?: field
    set(value) {
      val (propertyNamespace, propertyName) = parseName(value)
      namespace = propertyNamespace
      field = propertyName
      delegate = findDelegate(propertyNamespace, propertyName)
      delegateUpdated(this)

      // Give the model a change to hide expanded flag items
      model.firePropertyValueChangeIfNeeded()
    }

  override val type: NlPropertyType
    get() = delegate?.type ?: NlPropertyType.UNKNOWN

  override val definition: AttributeDefinition?
    get() = delegate?.definition

  override val components: List<NlComponent>
    get() = delegate?.components ?: emptyList()

  override val componentName: String
    get() = delegate?.componentName ?: ""

  override val libraryName: String
    get() = delegate?.libraryName ?: ""

  override fun isSameProperty(qualifiedName: String): Boolean {
    val (propertyNamespace, propertyName) = parseName(qualifiedName)
    return name == propertyName && namespace == propertyNamespace
  }

  // There should only be one instance of NeleNewPropertyItem per Property panel.
  override fun equals(other: Any?) = other is NlNewPropertyItem
  // The hashCode can be an arbitrary number since we only have 1 instance
  override fun hashCode() = 517

  /**
   * When the property name is set to something valid, the [delegate] will be not null.
   * All remaining properties and functions should delegate to this [delegate] if present.
   */
  override var delegate: NlPropertyItem? = null
    private set

  override val nameEditingSupport = object : EditingSupport {
    override val completion: EditorCompletion = { getPropertyNamesWithPrefix() }
    override val allowCustomValues = false
    override val validation = { text: String? -> validateName(text) }
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

  override val colorButton: ActionIconButton?
    get() = delegate?.colorButton

  override val children: List<NlFlagPropertyItem>
    get() = (delegate as? NlFlagsPropertyItem)?.children ?: emptyList()

  override fun flag(itemName: String): NlFlagPropertyItem? {
    return (delegate as? NlFlagsPropertyItem)?.flag(itemName)
  }

  override val maskValue: Int
    get() = (delegate as? NlFlagsPropertyItem)?.maskValue ?: 0

  override val firstComponent: NlComponent?
    get() = properties.first?.components?.firstOrNull()

  private fun parseName(value: String): Pair<String, String> {
    val prefixIndex = value.indexOf(":")
    if (prefixIndex < 0) {
      return Pair("", value)
    }
    val prefix = value.substring(0, prefixIndex)
    val name = value.substring(prefixIndex + 1)
    val namespace = namespaceResolver.prefixToUri(prefix) ?: if (prefix == TOOLS_PREFIX) TOOLS_URI else ANDROID_URI
    return Pair(namespace, name)
  }

  private fun findDelegate(propertyNamespace: String, propertyName: String): NlPropertyItem? {
    var property = properties.getOrNull(propertyNamespace, propertyName)
    if (property != null) {
      return property
    }
    if (delegate?.name == propertyName) {
      return delegate
    }
    if (propertyNamespace == TOOLS_URI || propertyNamespace.isEmpty()) {
      for (ns in properties.namespaces) {
        property = properties.getOrNull(ns, propertyName)
        if (property != null) {
          return if (propertyNamespace == TOOLS_URI) property.designProperty else property
        }
      }
    }
    return null
  }

  private fun getPropertyNamesWithPrefix(): List<String> {
    val resolver = namespaceResolver
    val result = properties.values
      .filter { filter(it) }
      .map { getPropertyNameWithPrefix(it, resolver) }
      .toMutableList()
    properties.values
      .filter {
        it.designProperty.rawValue == null &&
        it.name != ATTR_STYLE &&
        properties.getOrNull(TOOLS_URI, it.name) == null
      }
      .mapTo(result) { getPropertyNameWithPrefix(it.designProperty, resolver) }
    return result
  }

  private fun getPropertyNameWithPrefix(property: NlPropertyItem, resolver: ResourceNamespace.Resolver): String {
    val name = property.name
    val prefixFromResolver = resolver.uriToPrefix(property.namespace)
    val prefix = if (prefixFromResolver.isNullOrEmpty() && property.namespace == TOOLS_URI) TOOLS_PREFIX else prefixFromResolver
    return if (prefix.isNullOrEmpty()) name else "$prefix:$name"
  }

  private fun validateName(text: String?): Pair<EditingErrorCategory, String> {
    val value = text.orEmpty()
    val (propertyNamespace, propertyName) = parseName(value)
    val property = findDelegate(propertyNamespace, propertyName)
    return when {
      value.isEmpty() -> EDITOR_NO_ERROR
      property == null -> Pair(EditingErrorCategory.ERROR, "No property found by the name: '$value'")
      property.rawValue != null -> Pair(EditingErrorCategory.ERROR, "A property by the name: '$value' is already specified")
      else -> EDITOR_NO_ERROR
    }
  }
}
