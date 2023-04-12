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
package com.android.tools.idea.common.model

import com.intellij.designer.model.EmptyXmlTag
import com.intellij.pom.Navigatable
import com.intellij.psi.xml.XmlTag

/**
 * NlComponent backend based on an empty XmlTag.
 * This is to be used for NlComponents that are not associated with an XML tag.
 */
class NlComponentBackendEmpty: NlComponentBackend {
  override val tag: XmlTag = EmptyXmlTag.INSTANCE

  override fun setTagElement(tag: XmlTag) { }

  @Deprecated("Use getTag", ReplaceWith("getTag()"))
  override fun getTagDeprecated() = tag

  override fun getTagName() = ""

  override fun getAttribute(attribute: String, namespace: String?) = null

  override fun setAttribute(attribute: String, namespace: String?, value: String?) = false

  override fun getAffectedFile() = null

  override fun reformatAndRearrange() { }

  override fun isValid() = true

  override fun getDefaultNavigatable() = null
}