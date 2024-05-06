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
package com.android.tools.idea.databinding.xml

import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.impl.DomManagerImpl
import com.intellij.xml.XmlElementDescriptor
import org.jetbrains.android.dom.layout.DataBindingElement

/**
 * [DataBindingDomElementDescriptorProvider] provides [DataBindingXmlTagDescriptor] for tags that
 * are [DataBindingElement]. Returns null, if tag is not [DataBindingElement].
 */
class DataBindingDomElementDescriptorProvider : XmlElementDescriptorProvider {
  override fun getDescriptor(tag: XmlTag): XmlElementDescriptor? {
    val domElement = DomManagerImpl.getDomManager(tag.project).getDomElement(tag)
    if (domElement !is DataBindingElement) {
      return null
    }
    return DataBindingXmlTagDescriptor(domElement)
  }
}
