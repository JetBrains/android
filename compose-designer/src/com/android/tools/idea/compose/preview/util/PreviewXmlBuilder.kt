/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.android.SdkConstants
import com.android.xml.XmlBuilder

/**
 * Interface to serialize [PreviewElement]s to XML.
 */
interface PreviewXmlBuilder {
  /**
   * Sets the root tag name. This method *must* be called before calling buildString.
   */
  fun setRootTagName(name: String): PreviewXmlBuilder

  /**
   * Adds a new attribute with [name] from the `tools` namespace with the given value.
   */
  fun toolsAttribute(name: String, value: String): PreviewXmlBuilder

  /**
   * Adds a new attribute with [name] from the `android` namespace with the given value.
   */
  fun androidAttribute(name: String, value: String): PreviewXmlBuilder

  /**
   * Returns the formatted XML constructed by this builder. If the root tag name has not been set by calling [setRootTagName], the
   * call with throw an [IllegalStateException].
   */
  fun buildString(): String
}


/**
 *  Implementation of [PreviewXmlBuilder] backed by [XmlBuilder]
 */
private class PreviewXmlBuilderImpl : PreviewXmlBuilder {
  private var rootTagName: String? = null
  private val attributes: MutableMap<String, String> = mutableMapOf()

  private fun addAttribute(prefix: String, name: String, value: String): PreviewXmlBuilder {
    attributes["$prefix:$name"] = value

    return this
  }

  override fun setRootTagName(name: String): PreviewXmlBuilder {
    rootTagName = name

    return this
  }

  override fun toolsAttribute(name: String, value: String): PreviewXmlBuilder = addAttribute(SdkConstants.TOOLS_NS_NAME, name, value)
  override fun androidAttribute(name: String, value: String): PreviewXmlBuilder = addAttribute(SdkConstants.ANDROID_NS_NAME, name, value)

  override fun buildString(): String {
    val rootTagName = rootTagName ?: throw IllegalStateException("no root tag name specified")

    val xmlBuilder = XmlBuilder()
      .startTag(rootTagName)
      .attribute(SdkConstants.XMLNS, SdkConstants.ANDROID_NS_NAME, SdkConstants.ANDROID_URI)
      .attribute(SdkConstants.XMLNS, SdkConstants.TOOLS_NS_NAME, SdkConstants.TOOLS_URI)

    attributes.forEach { (name, value) ->
      xmlBuilder.attribute(name, value)
    }

    return xmlBuilder.endTag(rootTagName).toString()
  }
}

/**
 * Interface to be implemented by serializable elements.
 */
interface XmlSerializable {
  /**
   * Generates the XML string wrapper for one [PreviewElement].
   * @param xmlBuilder output [PreviewXmlBuilderImpl] used to output the resulting XML.
   */
  fun toPreviewXml(xmlBuilder: PreviewXmlBuilder = PreviewXmlBuilderImpl()): PreviewXmlBuilder
}