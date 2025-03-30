/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("XmlParser")

package com.android.tools.rendering.parsers

import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Parses xml from [xmlString] into a [RenderXmlTag] hierarchy returning root tag. */
fun parseRootTag(xmlString: String): RenderXmlTag {
  val parser = KXmlParser()
  parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
  parser.setInput(StringReader(xmlString))
  return getTags(parser)[0]
}

private fun getTags(parser: XmlPullParser): List<RenderXmlTagImpl> {
  var eventType = parser.eventType
  val tags: MutableList<RenderXmlTagImpl> = ArrayList()
  while (XmlPullParser.END_DOCUMENT != eventType && XmlPullParser.END_TAG != eventType) {
    if (XmlPullParser.START_TAG == eventType) {
      val namespaceCount = parser.getNamespaceCount(parser.depth)
      val prefixToNamespace =
        (0 until namespaceCount).map {
          val prefix = parser.getNamespacePrefix(it)
          val namespace = parser.getNamespace(prefix)
          prefix to namespace
        }
      val namespaceMap = prefixToNamespace.toMap()
      val reverseNamespaceMap = prefixToNamespace.associate { it.second to it.first }
      val attrs =
        (0 until parser.attributeCount).map {
          val name = parser.getAttributeName(it)
          val namespace = parser.getAttributeNamespace(it)
          val prefix = reverseNamespaceMap[namespace] ?: ""
          val value = parser.getAttributeValue(it)
          RenderXmlAttributeImpl(value, name, namespace, prefix)
        }
      val tagName = parser.name
      val tagNamespace = parser.namespace
      parser.next()
      val childTags = getTags(parser)
      val newTag = RenderXmlTagImpl(tagName, tagNamespace, namespaceMap, childTags, attrs)
      childTags.forEach { it.parentTag = newTag }
      tags.add(newTag)
      eventType = parser.eventType
    } else {
      eventType = parser.next()
    }
  }
  if (XmlPullParser.END_DOCUMENT != eventType) {
    parser.next()
  }
  return tags
}
