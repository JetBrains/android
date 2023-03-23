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
package com.android.tools.idea.rendering.parsers

import com.intellij.openapi.project.Project

/** Implementation agnostic representation of a xml tag. Used in rendering pipeline. */
interface RenderXmlTag {
  val localNamespaceDeclarations: Map<String, String>

  fun getAttribute(name: String, namespace: String): RenderXmlAttribute?

  fun getAttribute(name: String): RenderXmlAttribute?

  val name: String

  val subTags: List<RenderXmlTag>

  val namespace: String

  val localName: String

  val isValid: Boolean

  val attributes: List<RenderXmlAttribute>

  val namespacePrefix: String

  val parentTag: RenderXmlTag?

  fun getAttributeValue(name: String): String?

  fun getAttributeValue(name: String, namespace: String): String?

  fun getNamespaceByPrefix(prefix: String): String

  fun getPrefixByNamespace(namespace: String): String?

  val project: Project

  val containingFileNameWithoutExtension: String

  val isEmpty: Boolean
}