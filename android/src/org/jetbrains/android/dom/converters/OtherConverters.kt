/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.dom.converters

import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.resolve
import com.android.tools.idea.util.androidFacet
import com.intellij.util.text.nullize
import com.intellij.util.xml.Converter
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.WrappingConverter
import org.jetbrains.android.dom.AndroidDomUtil
import org.jetbrains.android.dom.resources.StyleItem
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

class StyleItemConverter : WrappingConverter() {
  override fun getConverter(element: GenericDomValue<*>): Converter<*>? {
    val attributeName = (element as StyleItem).name
    val psiElement = attributeName.xmlAttribute ?: return null

    // Use the DOM for finding the module+facet, as for AAR sources this will be one of the
    // consuming modules and not null.
    val facet = element.androidFacet ?: return null

    val name = attributeName.stringValue.nullize(nullizeSpaces = true) ?: return null
    val reference = ResourceUrl.parseAttrReference(name)?.resolve(psiElement) ?: return null
    val resourceManager =
      ModuleResourceManagers.getInstance(facet).getResourceManager(reference.namespace.packageName)
        ?: return null
    val attrDefinition =
      resourceManager.attributeDefinitions?.getAttrDefinition(reference) ?: return null

    return AndroidDomUtil.getConverter(attrDefinition)
  }
}

/**
 * Adapts a given [Converter] to treat any string starting with '@' as a string resource reference.
 */
class StringResourceAdapterConverter(private val innerConverter: Converter<*>) :
  WrappingConverter() {
  override fun getConverter(domElement: GenericDomValue<*>): Converter<*> {
    return if (domElement.rawText?.startsWith('@') == true) {
      ResourceReferenceConverter(ResourceType.STRING, true, true)
    } else {
      innerConverter
    }
  }
}
