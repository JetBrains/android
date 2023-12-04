/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.intellij.util.xml.DomElement
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.XmlElementDescriptor.CONTENT_TYPE_CHILDREN
import com.intellij.xml.XmlElementDescriptor.CONTENT_TYPE_EMPTY
import com.intellij.xml.impl.dom.DomElementXmlDescriptor
import org.jetbrains.android.dom.layout.Data

/**
 * [DataBindingXmlTagDescriptor] is a wrapper around [DomElementXmlDescriptor] to which most of its
 * function calls are delegated. It overrides [getContentType] to provide custom functionality for
 * data binding tags.
 */
class DataBindingXmlTagDescriptor(private val dataBindingElement: DomElement) :
  XmlElementDescriptor by DomElementXmlDescriptor(dataBindingElement) {

  /**
   * The return value dictates what type of xml element this is. In the context of autocompletion,
   * this dictates what kind of xml autocompletion is performed.
   *
   * [CONTENT_TYPE_CHILDREN] will indicate this element can contain children and autocompletion will
   * populate both start and end tags. Ex: <tag></tag> [CONTENT_TYPE_EMPTY] indicates this is an
   * empty tag and autocompletion will populate an empty tag. Ex <tag/>.
   */
  override fun getContentType(): Int {
    if (dataBindingElement is Data) {
      return CONTENT_TYPE_CHILDREN
    } else {
      return CONTENT_TYPE_EMPTY
    }
  }
}
