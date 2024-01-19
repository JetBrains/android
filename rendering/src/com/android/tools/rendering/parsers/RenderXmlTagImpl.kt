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
package com.android.tools.rendering.parsers

import com.android.ide.common.rendering.api.ResourceNamespace

/** Simple implementation of [RenderXmlTag] assuming it is not related to any file. */
internal class RenderXmlTagImpl(
  override val localName: String,
  override val namespace: String,
  private val prefixToNamespace: Map<String, String>,
  override val subTags: List<RenderXmlTag>,
  attrs: List<RenderXmlAttribute>,
) : RenderXmlTag {
  private val attrsMap = attrs.associateBy { it.localName }
  override var parentTag: RenderXmlTag? = null
  private val namespaceToPrefix = prefixToNamespace.map { it.value to it.key }.toMap()
  override val namespacePrefix = namespaceToPrefix[namespace] ?: ""
  override val name: String =
    if (namespacePrefix.isEmpty()) localName else "$namespacePrefix:$localName"

  override val localNamespaceDeclarations: Map<String, String> = prefixToNamespace

  override fun getAttribute(name: String, namespace: String): RenderXmlAttribute? {
    return attrsMap[name]?.let { if (it.namespace == namespace) it else null }
  }

  override fun getAttribute(name: String): RenderXmlAttribute? {
    return attrsMap[name]
  }

  override val resourceNamespace: ResourceNamespace?
    get() = null

  override val isValid: Boolean
    get() = true

  override val attributes: List<RenderXmlAttribute>
    get() = attrsMap.values.toList()

  override fun getAttributeValue(name: String): String? {
    return getAttribute(name)?.value
  }

  override fun getAttributeValue(name: String, namespace: String): String? {
    return getAttribute(name, namespace)?.value
  }

  override fun getNamespaceByPrefix(prefix: String): String = prefixToNamespace[prefix]!!

  override fun getPrefixByNamespace(namespace: String): String? = namespaceToPrefix[namespace]

  override val containingFileNameWithoutExtension: String = ""
  override val isEmpty: Boolean = false
}
