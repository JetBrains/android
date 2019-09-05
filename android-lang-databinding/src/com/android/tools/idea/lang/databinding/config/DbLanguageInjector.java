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
package com.android.tools.idea.lang.databinding.config;

import static com.android.SdkConstants.PREFIX_BINDING_EXPR;
import static com.android.SdkConstants.PREFIX_TWOWAY_BINDING_EXPR;
import static com.android.SdkConstants.TAG_LAYOUT;

import com.android.tools.idea.databinding.util.DataBindingUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class DbLanguageInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (!(host instanceof XmlAttributeValue && host.getContainingFile() instanceof XmlFile)) {
      return;
    }

    if (!((XmlFile)host.getContainingFile()).getRootTag().getName().equals(TAG_LAYOUT)) {
      return;
    }

    String valueText = ((XmlAttributeValue)host).getValue();
    if (!DataBindingUtil.isBindingExpression(valueText)) {
      return;
    }

    String prefix = valueText.startsWith(PREFIX_TWOWAY_BINDING_EXPR) ? PREFIX_TWOWAY_BINDING_EXPR : PREFIX_BINDING_EXPR;
    // Parser only parses the expression, not the prefix '@{' or the suffix '}'. Extract the start/end index of the expression.
    String unescapedValue = host.getText();
    int startIndex = unescapedValue.indexOf(prefix.charAt(0)) + prefix.length();
    int endIndex;
    if (valueText.endsWith("}")) {
      endIndex = unescapedValue.lastIndexOf('}');
    }
    else {
      if (host.getNode().getLastChildNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        endIndex = host.getLastChild().getStartOffsetInParent();
      }
      else {
        endIndex = unescapedValue.length();
      }
    }
    if (endIndex == startIndex) {
      // No expression found.
      return;
    }
    injectionPlacesRegistrar.addPlace(DbLanguage.INSTANCE, TextRange.from(startIndex, endIndex - startIndex), null, null);
  }
}
