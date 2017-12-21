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
open class SimpleProperty(val myName: String, val myComponents: List<NlComponent>, private val myNamespace: String? = null,
                          val myValue: String? = null) : NlProperty {
  override fun getNamespace() = myNamespace

  override fun getResolvedValue() = null

  override fun isDefaultValue(value: String?) = true

  override fun getName() = myName

  override fun getValue(): String? = myValue

  override fun setValue(value: Any?) {}

  override fun resolveValue(value: String?) = null

  override fun getTooltipText() = ""

  override fun getDefinition() = null

  override fun getComponents() = myComponents

  override fun getResolver(): ResourceResolver? = model.configuration.resourceResolver

  override fun getModel() = myComponents[0].model

  override fun getTag() = if (myComponents.size > 1) null else myComponents[0].tag

  override fun getTagName() = tag?.localName

  override fun getChildProperty(name: String): NlProperty = throw UnsupportedOperationException(name)

  override fun getDesignTimeProperty() = throw IllegalStateException()
}