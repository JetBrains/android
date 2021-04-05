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
package com.android.tools.idea.uibuilder.property.support

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.idea.uibuilder.property.NelePropertyItem
import com.intellij.openapi.util.text.StringUtil

private const val TEXT_APPEARANCE = "TextAppearance"
private const val TEXT_APPEARANCE_APPCOMPAT = "TextAppearance.AppCompat"
private const val TEXT_APPEARANCE_DOT = "TextAppearance."
private const val APPCOMPAT_DOT = "TextAppearance.AppCompat."
private const val MATERIAL_DOT = "TextAppearance.Material."
private const val SMALL = "Small"
private const val MEDIUM = "Medium"
private const val LARGE = "Large"
private const val BODY1 = "Body1"
private const val BODY2 = "Body2"
private const val DISPLAY1 = "Display1"
private const val DISPLAY2 = "Display2"
private const val DISPLAY3 = "Display3"
private const val DISPLAY4 = "Display4"

/**
 * [EnumSupport] for the "textAppearance" attribute.
 *
 * We will find all transitive derived styles from the framework TextAppearance
 * style. The resulting styles are organized in a tree with the following headings:
 *    "Project", "Library", "AppCompat", "Android"
 * where "Project" are the user defined styles.
 *
 * The Android and AppCompat TextAppearances are limited to the following:
 *   Small, Medium, Large, Body1, Body2, Display1, Display2, Display3, Display4
 */
class TextAppearanceEnumSupport(property: NelePropertyItem): StyleEnumSupport(property) {

  companion object {
    // TODO: Replace with namespace for the appcompat styles
    private val appcompatNamespace = ResourceNamespace.TODO()
    private val accepted = setOf(SMALL, MEDIUM, LARGE, BODY1, BODY2, DISPLAY1, DISPLAY2, DISPLAY3, DISPLAY4)
  }

  override fun generate(): List<EnumValue> {
    val base = findBaseStyle(ResourceNamespace.ANDROID, TEXT_APPEARANCE) ?: return emptyList()
    val appcompat = findBaseStyle(appcompatNamespace, TEXT_APPEARANCE_APPCOMPAT)
    val includeMaterialStyles = appcompat == null
    val filter = { style: StyleResourceValue -> styleFilter(style, includeMaterialStyles) }
    val sortOrder = { style: StyleResourceValue -> displayName(style) }
    val styles = derivedStyles.find(base, filter, sortOrder)
    return convertStyles(styles)
  }

  private fun styleFilter(style: StyleResourceValue, includeMaterialStyles: Boolean): Boolean {
    if (style.isUserDefined) return true
    if (includeMaterialStyles && style.namespace != ResourceNamespace.ANDROID) return false
    if (!includeMaterialStyles && style.namespace != ResourceNamespace.TODO()) return false

    val base = if (includeMaterialStyles) MATERIAL_DOT else APPCOMPAT_DOT
    if (!style.name.startsWith(base)) return false
    return accepted.contains(StringUtil.trimStart(style.name, base))
  }

  override fun displayName(style: StyleResourceValue): String {
    val name = style.name
    return when {
      name.startsWith(APPCOMPAT_DOT) -> StringUtil.trimStart(name, APPCOMPAT_DOT)
      name.startsWith(MATERIAL_DOT) -> StringUtil.trimStart(name, MATERIAL_DOT)
      name.startsWith(TEXT_APPEARANCE_DOT) -> StringUtil.trimStart(name, TEXT_APPEARANCE_DOT)
      else -> name
    }
  }

  private fun findBaseStyle(namespace: ResourceNamespace, styleName: String): StyleResourceValue? {
    val reference = ResourceReference(namespace, ResourceType.STYLE, styleName)
    return resolver?.getStyle(reference)
  }
}
