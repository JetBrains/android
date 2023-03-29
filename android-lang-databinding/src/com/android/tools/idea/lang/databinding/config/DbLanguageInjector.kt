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
package com.android.tools.idea.lang.databinding.config

import com.android.SdkConstants.PREFIX_BINDING_EXPR
import com.android.SdkConstants.PREFIX_TWOWAY_BINDING_EXPR
import com.android.SdkConstants.TAG_LAYOUT

import com.android.utils.isBindingExpression
import com.intellij.openapi.util.TextRange
import com.intellij.psi.InjectedLanguagePlaces
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTokenType

class DbLanguageInjector : LanguageInjector {
  override fun getLanguagesToInject(host: PsiLanguageInjectionHost, injectionPlacesRegistrar: InjectedLanguagePlaces) {
    if ((host.containingFile as? XmlFile)?.rootTag?.name != TAG_LAYOUT) {
      return
    }

    val valueText = (host as? XmlAttributeValue)?.value ?: return
    if (!isBindingExpression(valueText)) {
      return
    }

    val prefix = if (valueText.startsWith(PREFIX_TWOWAY_BINDING_EXPR)) PREFIX_TWOWAY_BINDING_EXPR else PREFIX_BINDING_EXPR
    // Parser only parses the expression, not the prefix '@{' or the suffix '}'. Extract the start/end index of the expression.
    val unescapedValue = host.getText()
    val startIndex = unescapedValue.indexOf(prefix[0]) + prefix.length
    val endIndex: Int
    if (valueText.endsWith("}")) {
      endIndex = unescapedValue.lastIndexOf('}')
    }
    else {
      if (host.getNode().lastChildNode.elementType === XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        endIndex = host.getLastChild().startOffsetInParent
      }
      else {
        endIndex = unescapedValue.length
      }
    }
    if (endIndex == startIndex) {
      // No expression found.
      return
    }
    injectionPlacesRegistrar.addPlace(DbLanguage.INSTANCE, TextRange.from(startIndex, endIndex - startIndex), null, null)
  }
}
