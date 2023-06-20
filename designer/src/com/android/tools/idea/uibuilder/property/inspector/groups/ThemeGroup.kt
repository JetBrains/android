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
package com.android.tools.idea.uibuilder.property.inspector.groups

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.GroupSpec
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.dom.attrs.AttributeDefinition
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

private const val THEME_STYLEABLE = "Theme"

class ThemeGroup(facet: AndroidFacet, properties: PropertiesTable<NlPropertyItem>): GroupSpec<NlPropertyItem> {
  private val themeProperty = properties.getOrNull(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
  private val attrs = findThemeAttrs(facet)

  override val name: String
    get() = "theme"

  override val value: String?
    get() = themeProperty?.value

  override val itemFilter: (NlPropertyItem) -> Boolean
    get() = { it == themeProperty || it.definition?.let { def -> attrs.contains(def) } == true }

  override val comparator: Comparator<PTableItem>
    get() = FilteredPTableModel.alphabeticalSortOrder

  private fun findThemeAttrs(facet: AndroidFacet): Set<AttributeDefinition> {
    val definitions = ModuleResourceManagers.getInstance(facet).frameworkResourceManager?.attributeDefinitions
    val styleable = definitions?.getStyleableDefinition(ResourceReference.styleable(ResourceNamespace.ANDROID, THEME_STYLEABLE))
    return styleable?.attributes?.toHashSet() ?: emptySet()
  }
}
