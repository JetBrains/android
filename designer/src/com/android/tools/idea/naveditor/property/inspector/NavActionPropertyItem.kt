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
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.NlProperty
import com.google.common.collect.ImmutableList
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.dom.attrs.AttributeDefinition

/**
 * TODO
 */
class NavActionPropertyItem(val myName: String, val myComponent: NlComponent) : NlProperty {
  override fun getNamespace(): String? {
    return null
  }

  override fun getResolvedValue(): String? {
    return null
  }

  override fun isDefaultValue(value: String?): Boolean {
    return true
  }

  override fun getName(): String {
    return myName
  }

  override fun getValue(): String? {
    return null
  }

  override fun setValue(value: Any?) {
  }

  override fun resolveValue(value: String?): String? {
    return null
  }

  override fun getTooltipText(): String {
    // TODO
    return ""
  }

  override fun getDefinition(): AttributeDefinition? {
    return null
  }

  override fun getComponents(): List<NlComponent> {
    return ImmutableList.of(myComponent)
  }

  override fun getResolver(): ResourceResolver? {
    return null
  }

  override fun getModel(): NlModel {
    return myComponent.model
  }

  override fun getTag(): XmlTag? {
    return myComponent.tag
  }

  override fun getTagName(): String? {
    return tag?.localName
  }

  override fun getChildProperty(name: String): NlProperty {
    throw IllegalStateException()
  }

  override fun getDesignTimeProperty(): NlProperty {
    throw IllegalStateException()
  }
}