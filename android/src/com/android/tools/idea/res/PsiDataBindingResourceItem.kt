/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.SdkConstants
import com.android.ide.common.resources.DataBindingResourceType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.xml.XmlTag

/**
 * Keeps resource items that are related to data binding, extracted from layout files.
 */
data class PsiDataBindingResourceItem(
  val name: String,
  val type: DataBindingResourceType,
  val xmlTag: XmlTag,
  val source: PsiResourceFile
) {

  private val myData: Map<String, String> = type.attributes.associate { attr ->
    attr to StringUtil.unescapeXml(this.xmlTag.getAttributeValue(attr))
  }

  /**
   * Use [.getTypeDeclaration] to get the type instead of this method.
   */
  fun getExtra(name: String): String? {
    return myData[name]
  }

  /**
   * Same as sanitized the output of [.getExtra] with [SdkConstants.ATTR_TYPE].
   */
  val typeDeclaration: String?
    get() {
      val type = getExtra(SdkConstants.ATTR_TYPE)
      return type?.replace('$', '.')
    }
}
