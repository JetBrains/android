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

import com.android.tools.idea.databinding.util.DataBindingUtil
import com.intellij.psi.xml.XmlAttribute

/** Studio specific [XmlAttribute]-based implementation of [RenderXmlAttribute]. */
class PsiXmlAttribute(private val attr: XmlAttribute) : RenderXmlAttribute {
  override val value: String?
    get() = attr.value

  override val isNamespaceDeclaration: Boolean
    get() = attr.isNamespaceDeclaration

  override val localName: String
    get() = attr.localName

  override val namespace: String
    get() = attr.namespace

  override val namespacePrefix: String
    get() = attr.namespacePrefix

  override val bindingExprDefault: String?
    get() = DataBindingUtil.getBindingExprDefault(attr)

  override val name: String
    get() = attr.name
}