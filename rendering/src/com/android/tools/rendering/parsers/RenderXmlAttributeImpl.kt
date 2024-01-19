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

/** Simple implementation of [RenderXmlAttribute]. */
internal class RenderXmlAttributeImpl(
  override val value: String?,
  override val localName: String,
  override val namespace: String,
  override val namespacePrefix: String,
) : RenderXmlAttribute {
  override val isNamespaceDeclaration: Boolean
    get() = name.startsWith("xmlns") && (name.length == 5 || name[5] == ':')

  override val bindingExprDefault: String?
    get() = null

  override val name: String =
    if (namespacePrefix.isEmpty()) localName else "$namespacePrefix:$localName"
}
