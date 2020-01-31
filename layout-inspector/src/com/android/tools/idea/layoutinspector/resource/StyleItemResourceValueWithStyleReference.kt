/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.ide.common.rendering.api.StyleItemResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue

/**
 * A style item with a reference to the style it was found in.
 */
class StyleItemResourceValueWithStyleReference(
  val style: StyleResourceValue,
  private val item: StyleItemResourceValue
) : StyleItemResourceValue {

  override fun getResourceType() = item.resourceType
  override fun getLibraryName() = item.libraryName
  override fun isUserDefined() = item.isUserDefined
  override fun setValue(value: String?) { item.value = value }
  override fun getAttrName() = item.attrName
  override fun getName() = item.attrName
  override fun getNamespace() = item.namespace
  override fun isFramework() = item.isFramework
  override fun getValue() = item.value
  override fun asReference() = item.asReference()
  override fun getNamespaceResolver() = item.namespaceResolver
  override fun getAttr() = item.attr
}
