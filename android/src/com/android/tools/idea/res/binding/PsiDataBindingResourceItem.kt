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
package com.android.tools.idea.res.binding

import com.android.SdkConstants
import com.android.ide.common.resources.DataBindingResourceType
import com.android.tools.idea.res.PsiResourceFile
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.xml.XmlTag

/**
 * Represents a resource item that is related to data binding, extracted from a layout file.
 */
data class PsiDataBindingResourceItem(
  val name: String,
  val type: DataBindingResourceType,
  val xmlTag: XmlTag) {

  /**
   * If you are planning to call this with [SdkConstants.ATTR_TYPE], use [typeDeclaration] instead.
   */
  fun getExtra(name: String): String? {
    return type.attributes
      .find { it == name }
      ?.let { attr -> StringUtil.unescapeXml(xmlTag.getAttributeValue(attr)) }
  }

  /**
   * Same as sanitized output of [getExtra(SdkConstants.ATTR_TYPE)](getExtra).
   */
  val typeDeclaration: String?
    get() = getExtra(SdkConstants.ATTR_TYPE)?.replace('$', '.')
}
