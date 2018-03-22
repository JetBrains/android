// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.uibuilder.model.viewHandler
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import org.jetbrains.android.dom.attrs.AttributeDefinition
import java.util.*

private const val TEXT_APPEARANCE_SUFFIX = "TextAppearance"

/**
 * Given a [NelePropertyItem] compute the [EnumSupport] of the attribute if applicable.
 */
class NeleEnumSupportProvider : EnumSupportProvider<NelePropertyItem> {

  /**
   * Return the [EnumSupport] for a given property.
   *
   * @param property the property we want the [EnumSupport] for.
   * @return the [EnumSupport] for the property or null if not applicable.
   */
  override fun invoke(property: NelePropertyItem): EnumSupport? {
    return provideEnumSupportFromViewHandler(property.name, property.components) ?:
        getDropDownValuesFromSpecialCases(property) ?:
        property.definition?.let { provideEnumSupportFromAttributeDefinition(it) }
  }

  private val textSizeEnumSupport: EnumSupport by lazy {
    EnumSupport.simple(
      "8sp",
      "10sp",
      "12sp",
      "14sp",
      "18sp",
      "24sp",
      "30sp",
      "36sp"
    )
  }

  private val typefaceEnumSupport: EnumSupport by lazy {
    EnumSupport.simple("normal", "sans", "serif", "monospace")
  }

  private val sizesSupport: EnumSupport by lazy {
    EnumSupport.simple("match_parent", "wrap_content")
  }

  private fun provideEnumSupportFromViewHandler(name: String, components: List<NlComponent>): EnumSupport? {
    val isLayoutProperty = name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)
    val attrComponents = if (isLayoutProperty) getParentComponents(components) else components
    if (attrComponents.isEmpty()) {
      return null
    }
    var values: Map<String, String>? = null
    for (component in attrComponents) {
      val handler = component.viewHandler ?: return null
      val overrides = handler.getEnumPropertyValues(component).getOrDefault(name, null) ?: return null
      if (values == null) {
        values = overrides
      }
      else if (overrides != values) {
        return null
      }
    }
    if (values == null || values.isEmpty()) {
      return null
    }

    val enumValues = mutableListOf<EnumValue>()
    for ((value, display) in values.entries) {
      enumValues.add(EnumValue.item(value, display))
    }
    return EnumSupport.simple(enumValues)
  }

  private fun getParentComponents(components: Collection<NlComponent>): Collection<NlComponent> {
    val parents = IdentityHashMap<NlComponent, NlComponent>()
    components.stream()
        .map { it.parent }
        .forEach { if (it != null) parents.put(it, it) }
    return parents.keys
  }

  private fun findNlModel(components: List<NlComponent>): NlModel {
    return components[0].model
  }

  private fun getDropDownValuesFromSpecialCases(property: NelePropertyItem): EnumSupport? {
    val name = property.name
    if (name.endsWith(TEXT_APPEARANCE_SUFFIX)) {
      return TextAppearanceEnumSupport(property)
    }
    if (property.namespace != ANDROID_URI) {
      return null
    }
    return when (name) {
      ATTR_FONT_FAMILY -> getFontEnumSupport(property)
      ATTR_TYPEFACE -> typefaceEnumSupport
      ATTR_TEXT_SIZE -> textSizeEnumSupport
      ATTR_LINE_SPACING_EXTRA -> textSizeEnumSupport
      ATTR_TEXT_APPEARANCE -> TextAppearanceEnumSupport(property)
      ATTR_LAYOUT_HEIGHT,
      ATTR_LAYOUT_WIDTH,
      ATTR_DROPDOWN_HEIGHT,
      ATTR_DROPDOWN_WIDTH -> sizesSupport
      ATTR_ON_CLICK -> OnClickEnumSupport(findNlModel(property.components))
      ATTR_STYLE -> StyleEnumSupport(property)
      else -> null
    }
  }

  private fun getFontEnumSupport(property: NelePropertyItem): EnumSupport {
    val nlModel = findNlModel(property.components)
    val facet = nlModel.facet
    val resolver = nlModel.configuration.resourceResolver
    return FontEnumSupport(facet, resolver)
  }

  private fun provideEnumSupportFromAttributeDefinition(definition: AttributeDefinition): EnumSupport? {
    if (definition.values.isEmpty()) return null
    val valuesAsList = definition.values.map { EnumValue.item(it) }
    return EnumSupport.simple(valuesAsList)
  }
}
