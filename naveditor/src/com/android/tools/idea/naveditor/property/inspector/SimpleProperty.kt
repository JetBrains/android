/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty

/**
 * Basic property with simple implementations for some methods.
 */
open class SimpleProperty(private val _name: String, private val _components: List<NlComponent>, private val namespace: String? = null,
                          val _value: String? = null) : NlProperty {
  override fun getNamespace() = namespace

  override fun getResolvedValue() = null

  override fun isDefaultValue(value: String?) = true

  override fun getName() = _name

  override fun getValue(): String? = _value

  override fun setValue(value: Any?) {}

  override fun resolveValue(value: String?) = value

  override fun getTooltipText() = ""

  override fun getDefinition() = null

  override fun getComponents() = _components

  override fun getResolver(): ResourceResolver? = model.configuration.resourceResolver

  override fun getModel() = components[0].model

  override fun getTag() = if (components.size > 1) null else components[0].tag

  override fun getTagName() = tag?.localName

  override fun getChildProperty(name: String): NlProperty = throw UnsupportedOperationException(name)

  override fun getDesignTimeProperty() = throw IllegalStateException()
}