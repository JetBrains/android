/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.xml

import com.android.SdkConstants
import com.android.xml.XmlBuilder


/**
 *  A class to generate valid Xml layouts for custom View specified by [customViewFqcn]. Allows to specify Android (recognized by the
 *  Android framework) and Tooling (for custom behavior) attributes.
 */
class PreviewXmlBuilder(private val customViewFqcn: String) {
  private val attributes: MutableMap<String, String> = mutableMapOf()

  private fun addAttribute(prefix: String, name: String, value: String): PreviewXmlBuilder {
    attributes["$prefix:$name"] = value

    return this
  }

  fun toolsAttribute(name: String, value: String): PreviewXmlBuilder = addAttribute(SdkConstants.TOOLS_NS_NAME, name, value)
  fun androidAttribute(name: String, value: String): PreviewXmlBuilder = addAttribute(SdkConstants.ANDROID_NS_NAME, name, value)

  fun buildString(): String {
    val xmlBuilder = XmlBuilder()
      .startTag(customViewFqcn)
      .attribute(SdkConstants.XMLNS, SdkConstants.ANDROID_NS_NAME, SdkConstants.ANDROID_URI)
      .attribute(SdkConstants.XMLNS, SdkConstants.TOOLS_NS_NAME, SdkConstants.TOOLS_URI)

    attributes.forEach { (name, value) ->
      xmlBuilder.attribute(name, value)
    }

    return xmlBuilder.endTag(customViewFqcn).toString()
  }
}

/**
 * Interface that provides a custom view Xml Layout representation of the object.
 */
interface XmlSerializable {
  /**
   * Generates the mutable [PreviewXmlBuilder]. The callers of this method can further modify Xml attributed of the builder before
   * serializing it into a Xml Layout string.
   */
  fun toPreviewXml(): PreviewXmlBuilder
}