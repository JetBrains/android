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
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceUrl
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.intellij.openapi.util.text.StringUtil

const val PROJECT_HEADER = "Project"
const val LIBRARY_HEADER = "Library"
const val APPCOMPAT_HEADER = "AppCompat"
const val ANDROID_HEADER = "Android"
const val OTHER_HEADER = "Other"

/**
 * [EnumSupport] for the "style" attribute.
 *
 * We will find a base style for the current XML tag, and from that
 * find all transitive derived styles. The styles are organized in
 * a tree with the following headings:
 *    "Project", "Library", "AppCompat", "Android"
 * where "Project" are the user defined styles.
 */
open class StyleEnumSupport(val property: NlPropertyItem) : CachedEnumSupport {
  protected val facet = property.model.facet
  protected val resolver = property.resolver
  protected val derivedStyles = DerivedStyleFinder(facet, resolver)
  private var lastResult: List<EnumValue>? = null

  override val values: List<EnumValue>
    get() {
      return lastResult ?: cache(generate())
    }

  private fun cache(values: List<EnumValue>): List<EnumValue> {
    lastResult = values
    return values
  }

  protected open fun generate(): List<EnumValue> {
    val tagName = property.tagName
    if (tagName.isEmpty()) return emptyList()
    val baseStyles = getWidgetBaseStyles(tagName)
    val styles = derivedStyles.find(baseStyles, { true }, { style -> style.name })
    return convertStyles(styles)
  }

  protected open fun displayName(style: StyleResourceValue) = style.name

  /**
   * Convert the sorted list of styles into a sorted list of [EnumValue]s with group headers.
   */
  protected fun convertStyles(styles: List<StyleResourceValue>): List<EnumValue> {
    val resourceManager = StudioResourceRepositoryManager.getInstance(facet)
    val currentNamespace = resourceManager.namespace
    val namespaceResolver = property.namespaceResolver
    var prev: StyleResourceValue? = null
    val result = mutableListOf<EnumValue>()
    for (style in styles) {
      val xmlValue = style.asReference().getRelativeResourceUrl(currentNamespace, namespaceResolver).toString()
      val value = EnumValue.indented(xmlValue, displayName(style))
      if (prev != null && style.namespace == prev.namespace && style.libraryName == prev.libraryName) {
        result.add(value)
      }
      else {
        val header = when(style.namespace) {
          ResourceNamespace.ANDROID -> ANDROID_HEADER
          ResourceNamespace.TODO() -> determineHeaderFromLibraryName(style.libraryName)
          else -> StringUtil.getShortName(style.namespace.packageName ?: OTHER_HEADER, '.')
        }
        result.add(EnumValue.header(header))
        result.add(value)
      }
      prev = style
    }
    return result
  }

  private fun determineHeaderFromLibraryName(libraryName: String?) =
    when {
      libraryName.isNullOrEmpty() -> PROJECT_HEADER
      libraryName.contains("appcompat") -> APPCOMPAT_HEADER
      else -> LIBRARY_HEADER
    }

  private fun getWidgetBaseStyles(tagName: String): List<StyleResourceValue> {
    val manager = ViewHandlerManager.get(facet)
    val handler = manager.getHandler(tagName) ?: return emptyList()
    val possibleNames = handler.getBaseStyles(tagName)
    val prefixMap = handler.prefixToNamespaceMap
    return possibleNames.mapNotNull { resolve(it, prefixMap) }
  }

  private fun resolve(qualifiedStyleName: String, prefixMap: Map<String, String>): StyleResourceValue? {
    if (resolver == null) {
      return null
    }
    val url = ResourceUrl.parseStyleParentReference(qualifiedStyleName) ?: return null
    val reference = url.resolve(ResourceNamespace.ANDROID) { prefixMap[it] } ?: return null
    return resolver.getStyle(reference)
  }
}
